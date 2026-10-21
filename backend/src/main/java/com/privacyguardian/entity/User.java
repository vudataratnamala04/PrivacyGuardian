package com.privacyguardian.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity @Table(name="users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, unique=true, length=80) private String username;
  @Column(nullable=false, unique=true, length=160) private String email;
  @JsonIgnore
  @Column(nullable=false) private String passwordHash;
  @Column(nullable=false, length=20) private String role="USER";
  @Column(nullable=false) private boolean enabled=true;
  @Column(nullable=false) private int failedAttempts=0;
  private LocalDateTime lockedUntil;
  private LocalDateTime createdAt=LocalDateTime.now();
  public Long getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;}
  public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
  public String getRole(){return role;} public void setRole(String v){role=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
  public int getFailedAttempts(){return failedAttempts;} public void setFailedAttempts(int v){failedAttempts=v;} public LocalDateTime getLockedUntil(){return lockedUntil;} public void setLockedUntil(LocalDateTime v){lockedUntil=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
