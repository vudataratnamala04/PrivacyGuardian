package com.privacyguardian.dto;
public record AuthDtos() { public record Signup(String username,String email,String password){} public record Login(String username,String password){} public record AuthResponse(String token,String username,String role){} }
