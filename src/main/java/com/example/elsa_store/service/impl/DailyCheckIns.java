package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.dto.response.CheckInResponse;
import com.example.elsa_store.entity.DailyCheckIn;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.DailyCheckInRepository;
import com.example.elsa_store.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Điểm danh nhận xu: mỗi ngày một lần, chuỗi 7 ngày thưởng tăng dần ({@link #REWARDS}). Bỏ lỡ một ngày thì chuỗi tính
 * lại từ ngày 1; xong ngày 7 thì sang vòng mới. Chỉ tài khoản khách có ví xu.
 */
@Service
@Transactional
public class DailyCheckIns {

    /** Xu của ngày 1 → 7 trong chuỗi. */
    static final List<Integer> REWARDS = List.of(50, 50, 100, 100, 150, 150, 500);

    private final DailyCheckInRepository repository;
    private final UserRepository userRepository;
    private final CoinWallet coinWallet;
    private final Clock clock;

    @Autowired
    public DailyCheckIns(DailyCheckInRepository repository, UserRepository userRepository, CoinWallet coinWallet) {
        this(repository, userRepository, coinWallet, Clock.systemDefaultZone());
    }

    DailyCheckIns(DailyCheckInRepository repository, UserRepository userRepository, CoinWallet coinWallet, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.coinWallet = coinWallet;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CheckInResponse status(User user) {
        requireCustomer(user);
        return view(user, last(user), null);
    }

    /** Điểm danh hôm nay; bấm lần nữa trong ngày báo lỗi. Khoá tài khoản nên hai lần bấm cùng lúc chỉ được một lần. */
    public CheckInResponse checkIn(User user) {
        requireCustomer(user);
        userRepository.lockById(user.getId());
        LocalDate today = LocalDate.now(clock);
        if (repository.existsByUser_IdAndCheckDate(user.getId(), today)) {
            throw new AppException(ErrorCode.ALREADY_CHECKED_IN);
        }
        int day = nextDay(last(user), today);
        int coins = REWARDS.get(day - 1);
        DailyCheckIn saved = repository.save(DailyCheckIn.builder()
                .user(user)
                .checkDate(today)
                .streakDay(day)
                .coins(coins)
                .build());
        coinWallet.credit(user, coins, CoinTransactionType.DAILY_CHECK_IN, null,
                "Điểm danh ngày " + day + "/" + REWARDS.size());
        return view(user, saved, coins);
    }

    private DailyCheckIn last(User user) {
        return repository.findFirstByUser_IdOrderByCheckDateDesc(user.getId()).orElse(null);
    }

    /** Ngày của chuỗi cho lần điểm danh vào {@code day}: nối tiếp nếu hôm trước có điểm danh và chưa hết vòng. */
    static int nextDay(DailyCheckIn last, LocalDate day) {
        if (last == null || !last.getCheckDate().equals(day.minusDays(1)) || last.getStreakDay() >= REWARDS.size()) {
            return 1;
        }
        return last.getStreakDay() + 1;
    }

    private CheckInResponse view(User user, DailyCheckIn last, Integer awarded) {
        LocalDate today = LocalDate.now(clock);
        boolean doneToday = last != null && last.getCheckDate().equals(today);
        // Chuỗi còn sống nếu lần cuối là hôm nay hoặc hôm qua.
        boolean alive = last != null && (doneToday || last.getCheckDate().equals(today.minusDays(1)));
        int streak = alive ? last.getStreakDay() : 0;
        int next = nextDay(last, doneToday ? today.plusDays(1) : today);
        return new CheckInResponse(doneToday, streak, next, REWARDS.get(next - 1), REWARDS, awarded,
                coinWallet.balance(user));
    }

    private static void requireCustomer(User user) {
        if (!CoinWallet.eligible(user)) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
    }
}
