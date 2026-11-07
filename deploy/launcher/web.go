// Serve the built SPA and forward same-origin API requests to the local backend.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io/fs"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path"
	"strconv"
	"strings"
	"time"
)

func main() {
	port := flag.Int("port", 80, "public HTTP port")
	dist := flag.String("dist", "", "frontend dist directory")
	host := flag.String("host", "0.0.0.0", "listen address")
	backend := flag.Int("backend-port", 8080, "loopback API port")
	flag.Parse()
	if *port < 1 || *port > 65535 || *backend < 1 || *backend > 65535 || *dist == "" {
		log.Fatal("端口范围为 1–65535，必须提供前端目录")
	}
	root := os.DirFS(*dist)
	if _, err := fs.Stat(root, "index.html"); err != nil {
		log.Fatal("找不到网页，请先运行 install")
	}
	target := &url.URL{Scheme: "http", Host: net.JoinHostPort("127.0.0.1", strconv.Itoa(*backend))}
	client := &http.Client{Timeout: 4 * time.Second}
	resp, err := client.Get(target.String() + "/api/auth/csrf")
	if err != nil {
		log.Fatalf("无法连接本机后端 %s，请检查后端日志及本机 API 端口", target)
	}
	var csrf struct{ Token string }
	err = json.NewDecoder(resp.Body).Decode(&csrf)
	resp.Body.Close()
	if resp.StatusCode != 200 || err != nil || csrf.Token == "" {
		log.Fatal("后端尚未就绪，请检查后端日志或端口占用")
	}
	proxy := &httputil.ReverseProxy{Rewrite: func(r *httputil.ProxyRequest) {
		r.SetURL(target)
		r.Out.Host = r.In.Host
		r.SetXForwarded()
	}}
	files := http.FileServerFS(root)
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api" || strings.HasPrefix(r.URL.Path, "/api/") {
			proxy.ServeHTTP(w, r)
			return
		}
		name := strings.TrimPrefix(path.Clean(r.URL.Path), "/")
		if name != "" {
			info, err := fs.Stat(root, name)
			if err == nil && !info.IsDir() {
				files.ServeHTTP(w, r)
				return
			}
			if path.Ext(name) != "" || strings.HasPrefix(name, "assets/") {
				http.NotFound(w, r)
				return
			}
		}
		http.ServeFileFS(w, r, root, "index.html")
	})
	server := &http.Server{Addr: net.JoinHostPort(*host, strconv.Itoa(*port)), Handler: handler,
		ReadHeaderTimeout: 10 * time.Second, IdleTimeout: 90 * time.Second}
	listener, err := net.Listen("tcp4", server.Addr)
	if err != nil {
		log.Fatalf("无法监听 %s（检查端口占用及低端口权限）: %v", server.Addr, err)
	}
	fmt.Printf("C 课堂已启动 http://localhost:%d/  (%s)\n", *port, server.Addr)
	log.Fatal(server.Serve(listener))
}
