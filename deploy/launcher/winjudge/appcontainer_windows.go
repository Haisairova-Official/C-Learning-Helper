// Windows-specific addition to the pinned go-judge winc package.
package winc

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"syscall"
	"unsafe"

	"github.com/criyle/go-judge/env/pool"
	"github.com/criyle/go-judge/envexec"
	"golang.org/x/sys/windows"
)

var userenv = windows.NewLazySystemDLL("userenv.dll")
var createProfile = userenv.NewProc("CreateAppContainerProfile")
var deleteProfile = userenv.NewProc("DeleteAppContainerProfile")

type isolatedBuilder struct{ pool.EnvBuilder }

func NewIsolatedBuilder() (pool.EnvBuilder, error) {
	base, err := NewBuilder("")
	if err != nil {
		return nil, err
	}
	return isolatedBuilder{base}, nil
}

func (b isolatedBuilder) Build() (pool.Environment, error) {
	base, err := b.EnvBuilder.Build()
	if err != nil {
		return nil, err
	}
	e := base.(*Environment)
	if err := e.createProfile(); err != nil {
		e.Destroy()
		return nil, err
	}
	return e, nil
}

func (e *Environment) createProfile() error {
	var nonce [16]byte
	if _, err := rand.Read(nonce[:]); err != nil {
		return err
	}
	name, err := windows.UTF16PtrFromString("CLearningHelper." + hex.EncodeToString(nonce[:]))
	if err != nil {
		return err
	}
	var sid *windows.SID
	n := uintptr(unsafe.Pointer(name))
	hr, _, _ := createProfile.Call(n, n, n, 0, 0, uintptr(unsafe.Pointer(&sid)))
	if uint32(hr) != 0 {
		return fmt.Errorf("CreateAppContainerProfile: %#x", uint32(hr))
	}
	e.profileName, e.packageSID = name, sid
	// Only the current package can use its private, low-integrity work folders.
	for _, dir := range []string{e.root, e.tmp} {
		sd, err := windows.GetNamedSecurityInfo(dir, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
		if err != nil {
			return err
		}
		original, _, err := sd.DACL()
		if err != nil {
			return err
		}
		entry := windows.EXPLICIT_ACCESS{
			AccessPermissions: windows.FILE_GENERIC_READ | windows.FILE_GENERIC_WRITE | windows.FILE_GENERIC_EXECUTE | windows.DELETE,
			AccessMode:        windows.GRANT_ACCESS, Inheritance: windows.SUB_CONTAINERS_AND_OBJECTS_INHERIT,
			Trustee: windows.TRUSTEE{TrusteeForm: windows.TRUSTEE_IS_SID, TrusteeValue: windows.TrusteeValueFromSID(sid)},
		}
		acl, err := windows.ACLFromEntries([]windows.EXPLICIT_ACCESS{entry}, original)
		if err != nil {
			return err
		}
		if err := windows.SetNamedSecurityInfo(dir, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION, nil, nil, acl, nil); err != nil {
			return err
		}
	}
	return nil
}

func (e *Environment) removeProfile() {
	if e.packageSID != nil {
		deleteProfile.Call(uintptr(unsafe.Pointer(e.profileName)))
		windows.FreeSid(e.packageSID)
		e.packageSID = nil
	}
}

func (e *Environment) nativeCommand(param *envexec.ExecveParam) (string, error) {
	bin := os.Getenv("CASSIST_COMPILER_BIN")
	system := os.Getenv("SystemRoot")
	if !filepath.IsAbs(bin) || !filepath.IsAbs(system) {
		return "", errors.New("missing native toolchain")
	}
	path := filepath.Join(system, "System32")
	var exe string
	switch param.Args[0] {
	case "/usr/bin/gcc", "/usr/bin/g++":
		exe = filepath.Join(bin, filepath.Base(param.Args[0])+".exe")
		path = bin + ";" + path
		for i := 1; i < len(param.Args); i++ {
			if param.Args[i-1] == "-o" && param.Args[i] == "main" {
				param.Args[i] = "main.exe"
			}
		}
		param.Args = append(param.Args, "-static")
	case "./main":
		exe = filepath.Join(e.root, "main.exe")
	default:
		return "", errors.New("unsupported judge command")
	}
	param.Env = []string{"PATH=" + path, "SystemRoot=" + system, "WINDIR=" + system, "LC_ALL=C", "TMP=" + e.tmp, "TEMP=" + e.tmp}
	return exe, nil
}

// Create the process suspended inside the AppContainer. Only stdin/stdout/stderr
// are inherited. The caller attaches resource limits before resuming the thread.
func (e *Environment) startNative(param envexec.ExecveParam, exe string, pi *windows.ProcessInformation) error {
	if e.packageSID == nil {
		return errors.New("missing private AppContainer")
	}
	app, err := windows.UTF16PtrFromString(exe)
	if err != nil {
		return err
	}
	cmd, err := windows.UTF16PtrFromString(makeCmdLine(param.Args))
	if err != nil {
		return err
	}
	dir, err := windows.UTF16PtrFromString(e.root)
	if err != nil {
		return err
	}

	attrs, err := windows.NewProcThreadAttributeList(2)
	if err != nil {
		return err
	}
	defer attrs.Delete()
	caps := struct {
		Package         *windows.SID
		Capabilities    *windows.SIDAndAttributes
		Count, Reserved uint32
	}{Package: e.packageSID}
	const securityCapabilities = 0x00020009
	if err := attrs.Update(securityCapabilities, unsafe.Pointer(&caps), unsafe.Sizeof(caps)); err != nil {
		return err
	}
	syscall.ForkLock.Lock()
	defer syscall.ForkLock.Unlock()
	var handles [3]windows.Handle
	for i, file := range param.Files {
		if err := windows.DuplicateHandle(windows.CurrentProcess(), windows.Handle(file), windows.CurrentProcess(), &handles[i], 0, true, windows.DUPLICATE_SAME_ACCESS); err != nil {
			return err
		}
		defer windows.CloseHandle(handles[i])
	}
	if err := attrs.Update(windows.PROC_THREAD_ATTRIBUTE_HANDLE_LIST, unsafe.Pointer(&handles[0]), unsafe.Sizeof(handles)); err != nil {
		return err
	}
	si := windows.StartupInfoEx{ProcThreadAttributeList: attrs.List()}
	si.Cb = uint32(unsafe.Sizeof(si))
	si.Flags = windows.STARTF_USESTDHANDLES
	si.StdInput, si.StdOutput, si.StdErr = handles[0], handles[1], handles[2]
	err = windows.CreateProcess(app, cmd, nil, nil, true, windows.EXTENDED_STARTUPINFO_PRESENT|windows.CREATE_SUSPENDED|windows.CREATE_NO_WINDOW|windows.CREATE_UNICODE_ENVIRONMENT, createEnvBlock(param.Env), dir, &si.StartupInfo, pi)
	runtime.KeepAlive(caps)
	if err != nil {
		return fmt.Errorf("create isolated process: %w", err)
	}
	// Verify the effective token on the actual child before any student instruction runs.
	var token windows.Token
	if err = windows.OpenProcessToken(pi.Process, windows.TOKEN_QUERY, &token); err == nil {
		defer token.Close()
		var flag, size uint32
		const tokenIsAppContainer = 29
		err = windows.GetTokenInformation(token, tokenIsAppContainer, (*byte)(unsafe.Pointer(&flag)), 4, &size)
		if err == nil && flag != 1 {
			err = errors.New("child is outside the AppContainer")
		}
	}
	if err != nil {
		windows.TerminateProcess(pi.Process, 1)
	}
	return err
}
