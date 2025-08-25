// LoginResponse.java
package com.authjava.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
  private String status;
  private String token;
  private String message;
}
