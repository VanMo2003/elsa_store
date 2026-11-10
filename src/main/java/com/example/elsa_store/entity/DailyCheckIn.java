package com.example.elsa_store.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Một lần khách điểm danh nhận xu (mỗi khách mỗi ngày một dòng). */
@Entity
@Table(
        name = "daily_check_ins",
        uniqueConstraints = @UniqueConstraint(name = "uk_check_in_user_day", columnNames = {"user_id", "check_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyCheckIn extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Ngày điểm danh theo giờ Việt Nam. */
    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    /** Ngày thứ mấy trong chuỗi 7 ngày (1–7). */
    @Column(name = "streak_day", nullable = false)
    private Integer streakDay;

    @Column(name = "coins", nullable = false)
    private Integer coins;
}
