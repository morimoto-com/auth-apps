package com.authjava.controller;

import com.authjava.dto.LoginRequest;
import com.authjava.dto.LoginResponse;
import com.authjava.dto.UserInfoResponse;
import com.authjava.util.JwtUtil;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Slf4j
public class AuthController {

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    if ("test".equals(request.getLoginId()) && "pass".equals(request.getPassword())) {
      String token = JwtUtil.generateToken(request.getLoginId());
      return ResponseEntity.ok(new LoginResponse("success", token, null));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new LoginResponse("error", null, "認証失敗"));
  }

  @GetMapping("/userinfo")
  public ResponseEntity<UserInfoResponse> userinfo(@RequestHeader("Authorization") String authHeader) {
    log.info("java application");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      try {
        String username = JwtUtil.validateToken(token);
        return ResponseEntity.ok(new UserInfoResponse(username));
      } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
}
