package com.example.demo.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VisitStatsRepository extends JpaRepository<VisitStats, String> {

    // методы save(), findById() уже встроены
}