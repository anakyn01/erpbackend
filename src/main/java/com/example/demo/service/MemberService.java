package com.example.demo.service;

import com.example.demo.config.JwtProvider;
import com.example.demo.dto.MemberLoginDto;
import com.example.demo.dto.MemberRegisterDto;
import com.example.demo.dto.MemberUpdateDto;
import com.example.demo.entity.Member;
import com.example.demo.repository.MemberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JavaMailSender mailSender;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Transactional
    public void register(MemberRegisterDto dto) {
        if (memberRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다.");
        }

        Member member = Member.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .companyName(dto.getCompanyName())
                .position(dto.getPosition())
                .tel(dto.getTel())
                .address(dto.getAddress())
                .detailAddress(dto.getDetailAddress())
                .gender(dto.getGender())
                .build();

        memberRepository.save(member);
    }

    public String login(MemberLoginDto dto) {
        Member member = memberRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));
        if (!passwordEncoder.matches(dto.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("잘못된 비밀번호입니다.");
        }

        return jwtProvider.generateToken(member.getEmail());
    }

    @Transactional
    public void sendTempPassword(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        String tempPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        member.setPassword(passwordEncoder.encode(tempPassword));

        org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[ERP 시스템] 임시 비밀번호 발급 안내");
        message.setText("요청하신 임시 비밀번호는 [" + tempPassword + "] 입니다.\n로그인 후 마이페이지에서 반드시 비밀번호를 변경해 주세요.");

        mailSender.send(message);
    }

    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(oldPassword, member.getPassword())) {
            throw new IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.");
        }

        member.setPassword(passwordEncoder.encode(newPassword));
    }

    @Async
    @Transactional
    public CompletableFuture<String> kakaoLoginAsync(String code) {
        String kakaoAccessToken = getKakaoAccessToken(code);
        KakaoUserInfo userInfo = getKakaoUserInfo(kakaoAccessToken);
        Member member = memberRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Member newMember = Member.builder()
                    .firstName(userInfo.getNickname())
                    .lastName("카카오")
                    .email(userInfo.getEmail())
                    .password(passwordEncoder.encode("KAKAO_SOCIAL_USER"))
                    .provider("KAKAO")
                    .providerId(userInfo.getId())
                    .build();
            return memberRepository.save(newMember);
        });
        String token = jwtProvider.generateToken(member.getEmail());
        return CompletableFuture.completedFuture(token);
    }

    @Transactional
    public String kakaoLogin(String code) {
        String kakaoAccessToken = getKakaoAccessToken(code);
        KakaoUserInfo userInfo = getKakaoUserInfo(kakaoAccessToken);

        Member member = memberRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Member newMember = Member.builder()
                    .firstName(userInfo.getNickname())
                    .lastName("카카오")
                    .email(userInfo.getEmail())
                    .password(passwordEncoder.encode("KAKAO_SOCIAL_USER"))
                    .provider("KAKAO")
                    .providerId(userInfo.getId())
                    .build();
            return memberRepository.save(newMember);
        });
        return jwtProvider.generateToken(member.getEmail());
    }

    private String getKakaoAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", "43b4bfa2d77a904f4321f38325c03f09");
        params.add("client_secret", "cbg11djCLVm1AAcalhUoFjr7PrT4Fesk");
        params.add("redirect_uri", "http://heartbreakkid.co.kr/portfolio/erp/oauth/kakao/callback/");
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            return rootNode.path("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("카카오 엑세스 토큰 추출 실패", e);
        }
    }

    private KakaoUserInfo getKakaoUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            String id = rootNode.path("id").asText();
            String email = id + "@kakao.local";
            String nickname = rootNode.path("properties").path("nickname").asText();

            return new KakaoUserInfo(id, email, nickname);
        } catch (Exception e) {
            throw new RuntimeException("카카오 사용자 정보 조회 실패", e);
        }
    }

    @Transactional
    public void updateProfile(String email, MemberUpdateDto dto) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을수 없습니다"));
        member.updateProfile(dto.getTel(), dto.getAddress(), dto.getDetailAddress());
    }

    @Getter
    @AllArgsConstructor
    private static class KakaoUserInfo {
        private String id, email, nickname;
    }

    @Transactional
    public String googleLogin(String code) {
        String googleAccessToken = getGoogleAccessToken(code);
        GoogleUserInfo userInfo = getGoogleUserInfo(googleAccessToken);

        Member member = memberRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Member newMember = Member.builder()
                    .firstName(userInfo.getName())
                    .lastName("구글")
                    .email(userInfo.getEmail())
                    .password(passwordEncoder.encode("GOOGLE_SOCIAL_USER"))
                    .provider("GOOGLE")
                    .providerId(userInfo.getId())
                    .build();
            return memberRepository.save(newMember);
        });
        return jwtProvider.generateToken(member.getEmail());
    }

    private String getGoogleAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", "http://heartbreakkid.co.kr/portfolio/erp/oauth/google/callback/");
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            return rootNode.path("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("구글 액세스 토큰 추출 실패", e);
        }
    }

    private GoogleUserInfo getGoogleUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://www.googleapis.com/oauth2/v2/userinfo?access_token=" + accessToken;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            String id = rootNode.path("id").asText();
            String email = rootNode.path("email").asText();
            String name = rootNode.path("name").asText();

            return new GoogleUserInfo(id, email, name);
        } catch (Exception e) {
            throw new RuntimeException("구글 사용자 정보 조회 실패", e);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class GoogleUserInfo {
        private String id, email, name;
    }

    @Async
    @Transactional
    public CompletableFuture<String> googleLoginAsync(String code) {
        String googleAccessToken = getGoogleAccessToken(code);
        GoogleUserInfo userInfo = getGoogleUserInfo(googleAccessToken);

        Member member = memberRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Member newMember = Member.builder()
                    .firstName(userInfo.getName())
                    .lastName("구글")
                    .email(userInfo.getEmail())
                    .password(passwordEncoder.encode("GOOGLE_SOCIAL_USER"))
                    .provider("GOOGLE")
                    .providerId(userInfo.getId())
                    .build();
            return memberRepository.save(newMember);
        });

        String token = jwtProvider.generateToken(member.getEmail());
        return CompletableFuture.completedFuture(token);
    }
}