package com.privacyguardian.entity;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="audit_logs")
public class AuditLog { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; private String username; private String action; private String details; private String severity; private LocalDateTime createdAt=LocalDateTime.now();
 public Long getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getAction(){return action;} public void setAction(String v){action=v;} public String getDetails(){return details;} public void setDetails(String v){details=v;} public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;} public LocalDateTime getCreatedAt(){return createdAt;} }
