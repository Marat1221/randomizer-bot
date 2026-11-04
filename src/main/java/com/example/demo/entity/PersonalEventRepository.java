package com.example.demo.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalEventRepository extends JpaRepository<PersonalEvent, String> {
}