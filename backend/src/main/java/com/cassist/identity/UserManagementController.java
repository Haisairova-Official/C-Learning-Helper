package com.cassist.identity;

import com.cassist.common.PageResult;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {
    private final CurrentActor actors;
    private final UserManagementService users;
    public UserManagementController(CurrentActor actors,UserManagementService users) { this.actors=actors; this.users=users; }
    private Actor admin() { Actor actor=actors.require(); UserManagementService.requireAdmin(actor); return actor; }
    private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie").body(body); }
    @GetMapping public ResponseEntity<PageResult<UserManagementService.UserView>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String q) { return response(users.list(admin(),page,pageSize,q)); }
    @GetMapping("/{id}") public ResponseEntity<UserManagementService.UserView> get(@PathVariable String id) { return response(users.get(admin(),id)); }
    @PostMapping public ResponseEntity<UserManagementService.UserView> create(@RequestBody UserManagementService.Create input) { return response(users.create(admin(),input)); }
    @PutMapping("/{id}") public ResponseEntity<UserManagementService.UserView> edit(@PathVariable String id,@RequestBody UserManagementService.Edit input) { return response(users.edit(admin(),id,input)); }
    @GetMapping("/{id}/avatar") public ResponseEntity<byte[]> avatar(@PathVariable String id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary","Cookie").header("X-Content-Type-Options","nosniff").contentType(MediaType.IMAGE_PNG).body(users.avatar(admin(),id)); }
    @PostMapping(value="/{id}/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<UserManagementService.UserView> upload(@PathVariable String id,@RequestPart("file") MultipartFile file) { return response(users.avatar(admin(),id,file)); }
    @DeleteMapping("/{id}/avatar") public ResponseEntity<UserManagementService.UserView> remove(@PathVariable String id) { return response(users.avatar(admin(),id,null)); }
}
