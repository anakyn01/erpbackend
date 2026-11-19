package com.example.demo.controller;

import com.example.demo.config.JwtProvider;
import com.example.demo.dto.*;
import com.example.demo.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final JwtProvider jwtProvider;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody MemberRegisterDto requestDto) {
        try {
            memberService.register(requestDto);
            return ResponseEntity.ok("회원가입 성공");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("회원가입 처리 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody MemberLoginDto requestDto) {
        try {
            String token = memberService.login(requestDto);
            return ResponseEntity.ok(new TokenResponseDto(token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("로그인 처리 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            memberService.sendTempPassword(request.get("email"));
            return ResponseEntity.ok("임시 비밀번호가 이메일로 발송되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String token,
                                            @RequestBody PasswordChangeDto dto) {
        try {
            String actualToken = token.substring(7);
            String email = jwtProvider.getEmailFromToken(actualToken);

            memberService.changePassword(email, dto.getOldPassword(), dto.getNewPassword());
            return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader("Authorization") String token,
                                           @RequestBody MemberUpdateDto dto) {
        try {
            String actualToken = token.substring(7);
            String email = jwtProvider.getEmailFromToken(actualToken);
            memberService.updateProfile(email, dto);
            return ResponseEntity.ok("회원 정보가 성공적으로 수정되었습니다");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/kakao-login")
    public ResponseEntity<?> kakaoLogin(@RequestBody KakaoLoginDto dto) {
        try {
            String token = memberService.kakaoLogin(dto.getCode());
            return ResponseEntity.ok(new TokenResponseDto(token));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/google-login")
    public CompletableFuture<ResponseEntity<Object>> googleLogin(@RequestBody GoogleLoginDto dto) {
        return memberService.googleLoginAsync(dto.getCode())
                .thenApply(token -> ResponseEntity.ok().body((Object) new TokenResponseDto(token)))
                .exceptionally(ex -> ResponseEntity.badRequest().body("구글 로그인 처리 중 오류가 발생했습니다: " + ex.getMessage()));
    }
}