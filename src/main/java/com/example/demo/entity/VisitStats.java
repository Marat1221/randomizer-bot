package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class VisitStats {
    @Id
    private String pageId;    // колонка: page_id (главный ключ)
    private Long visitCount;  // колонка: visit_count

    public VisitStats() {}

    public VisitStats(String pageId, Long visitCount) {
        this.pageId = pageId;
        this.visitCount = visitCount;
    }

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public Long getVisitCount() { return visitCount; }
    public void setVisitCount(Long visitCount) { this.visitCount = visitCount; }
}