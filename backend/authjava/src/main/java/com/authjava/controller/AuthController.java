package com.authjava.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authjava.dto.LoginRequest;
import com.authjava.dto.LoginResponse;
import com.authjava.dto.UserInfoResponse;
import com.authjava.util.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  private final JwtUtil jwtUtil; // Spring により注入される

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    if ("test".equals(request.getLoginId()) && "pass".equals(request.getPassword())) {
      String token = jwtUtil.generateToken(request.getLoginId()); // ← インスタンス経由で呼ぶ
      return ResponseEntity.ok(new LoginResponse("success", token, null));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new LoginResponse("error", null, "認証失敗"));
  }

  @GetMapping("/userinfo")
  public ResponseEntity<UserInfoResponse> userinfo(
      @RequestHeader(value = "Authorization", required = false) String authHeader) {

    log.info("java application /userinfo");

    var result = jwtUtil.validate(authHeader); // ← インスタンス経由

    if (!result.valid) {
      // 401 + WWW-Authenticate で何が不正かを示す（RFC 6750）
      String wwwAuth = String.format(
          "Bearer error=\"invalid_token\", error_description=\"%s\"",
          result.errorCode != null ? result.errorCode : "invalid");
      return ResponseEntity
          .status(HttpStatus.UNAUTHORIZED)
          .header("WWW-Authenticate", wwwAuth)
          .build();
    }

    String username = result.claims.getSubject();
    return ResponseEntity.ok(new UserInfoResponse(username));
  }

}
