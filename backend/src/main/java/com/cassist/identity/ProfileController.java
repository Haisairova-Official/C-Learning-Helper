package com.cassist.identity;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final CurrentActor actors;
    private final ProfileService profiles;

    public ProfileController(CurrentActor actors, ProfileService profiles) {
        this.actors = actors; this.profiles = profiles;
    }

    @GetMapping
    public ResponseEntity<ProfileView> get() { return privateResponse(profiles.get(actors.require())); }

    @PutMapping
    public ResponseEntity<ProfileView> update(@Valid @RequestBody ProfileUpdate input) {
        return privateResponse(profiles.update(actors.require(), input));
    }

    public record TinoUnlock(String sequence) {}

    @PostMapping("/tino/unlock")
    public ResponseEntity<ProfileView> unlockTino(@RequestBody TinoUnlock input) {
        return privateResponse(profiles.unlockTino(actors.require(),input.sequence()));
    }

    @PostMapping(value="/avatar", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileView> upload(@RequestPart("file") MultipartFile file) {
        return privateResponse(profiles.uploadAvatar(actors.require(), file));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<ProfileView> delete() { return privateResponse(profiles.deleteAvatar(actors.require())); }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar() {
        byte[] png = profiles.getAvatar(actors.require());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Pragma", "no-cache")
                .header("Vary", "Cookie").header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_PNG).contentLength(png.length).body(png);
    }

    private ResponseEntity<ProfileView> privateResponse(ProfileView value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("Vary", "Cookie").body(value);
    }
}
