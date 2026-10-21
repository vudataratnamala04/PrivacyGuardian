package com.privacyguardian.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity @Table(name="file_records")
public class FileRecord {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false) private String originalName;
  private String storedOriginal;
  private String storedProtected;
  private String backupPath;
  private String sha256;
  private String riskLevel;
  private int riskScore;
  @Column(length=4000) private String detections;
  private boolean qrDetected;
  private boolean faceDetected;
  private boolean protectedFile;
  private String protectionMethod;
  private String protectedSha256;
  private String agentAction;
  private String agentRecommendedMethod;
  @Column(length=1200) private String agentReason;
  private double agentConfidence;
  private boolean agentExecuted;
  private LocalDateTime createdAt=LocalDateTime.now();
  @JsonIgnore
  @ManyToOne(fetch=FetchType.EAGER, optional=false) private User owner;
  public Long getId(){return id;} public String getOriginalName(){return originalName;} public void setOriginalName(String v){originalName=v;}
  public String getStoredOriginal(){return storedOriginal;} public void setStoredOriginal(String v){storedOriginal=v;} public String getStoredProtected(){return storedProtected;} public void setStoredProtected(String v){storedProtected=v;}
  public String getBackupPath(){return backupPath;} public void setBackupPath(String v){backupPath=v;} public String getSha256(){return sha256;} public void setSha256(String v){sha256=v;}
  public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String v){riskLevel=v;} public int getRiskScore(){return riskScore;} public void setRiskScore(int v){riskScore=v;}
  public String getDetections(){return detections;} public void setDetections(String v){detections=v;} public boolean isQrDetected(){return qrDetected;} public void setQrDetected(boolean v){qrDetected=v;}
  public boolean isFaceDetected(){return faceDetected;} public void setFaceDetected(boolean v){faceDetected=v;}
  public boolean isProtectedFile(){return protectedFile;} public void setProtectedFile(boolean v){protectedFile=v;}
  public String getProtectionMethod(){return protectionMethod;} public void setProtectionMethod(String v){protectionMethod=v;}
  public String getProtectedSha256(){return protectedSha256;} public void setProtectedSha256(String v){protectedSha256=v;}
  public String getAgentAction(){return agentAction;} public void setAgentAction(String v){agentAction=v;}
  public String getAgentRecommendedMethod(){return agentRecommendedMethod;} public void setAgentRecommendedMethod(String v){agentRecommendedMethod=v;}
  public String getAgentReason(){return agentReason;} public void setAgentReason(String v){agentReason=v;}
  public double getAgentConfidence(){return agentConfidence;} public void setAgentConfidence(double v){agentConfidence=v;}
  public boolean isAgentExecuted(){return agentExecuted;} public void setAgentExecuted(boolean v){agentExecuted=v;}
  public LocalDateTime getCreatedAt(){return createdAt;} public User getOwner(){return owner;} public void setOwner(User v){owner=v;}
}
