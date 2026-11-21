package com.gym.springapp.service;

import com.gym.springapp.exceptions.GymClosedException;
import com.gym.springapp.model.GymSchedule;
import com.gym.springapp.model.Holiday;
import com.gym.springapp.repository.GymScheduleRepo;
import com.gym.springapp.repository.HolidayRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class GymScheduleService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    private final GymScheduleRepo scheduleRepo;
    private final HolidayRepo holidayRepo;

    public GymScheduleService(GymScheduleRepo scheduleRepo, HolidayRepo holidayRepo) {
        this.scheduleRepo = scheduleRepo;
        this.holidayRepo = holidayRepo;
    }

    @PostConstruct
    public void initDefaultSchedules() {
        if (scheduleRepo.count() == 0) {
            // Mon - Fri: 06:00 to 22:00
            for (DayOfWeek day : DayOfWeek.values()) {
                GymSchedule schedule = new GymSchedule();
                schedule.setDayOfWeek(day);
                if (day == DayOfWeek.SUNDAY) {
                    schedule.setOpenTime(LocalTime.of(8, 0));
                    schedule.setCloseTime(LocalTime.of(20, 0));
                } else if (day == DayOfWeek.SATURDAY) {
                    schedule.setOpenTime(LocalTime.of(7, 0));
                    schedule.setCloseTime(LocalTime.of(21, 0));
                } else {
                    schedule.setOpenTime(LocalTime.of(6, 0));
                    schedule.setCloseTime(LocalTime.of(22, 0));
                }
                schedule.setOpen(true);
                scheduleRepo.save(schedule);
            }
        }
    }

    public void validateGymIsOpen() {
        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        LocalTime now = LocalTime.now(KOLKATA_ZONE);

        // 1. Holiday check
        Optional<Holiday> holidayOpt = holidayRepo.findByHolidayDate(today);
        if (holidayOpt.isPresent()) {
            throw new GymClosedException("The gym is closed today for " + holidayOpt.get().getDescription());
        }

        // 2. Schedule check
        DayOfWeek day = today.getDayOfWeek();
        GymSchedule schedule = scheduleRepo.findByDayOfWeek(day).orElseGet(() -> {
            GymSchedule def = new GymSchedule();
            def.setDayOfWeek(day);
            def.setOpenTime(LocalTime.of(6, 0));
            def.setCloseTime(LocalTime.of(22, 0));
            def.setOpen(true);
            return def;
        });

        if (!schedule.isOpen()) {
            throw new GymClosedException("The gym is closed today (" + day.name() + ")");
        }

        if (now.isBefore(schedule.getOpenTime()) || now.isAfter(schedule.getCloseTime())) {
            throw new GymClosedException("The gym is closed right now. Operating hours today: "
                    + schedule.getOpenTime().format(TIME_FORMATTER) + " - "
                    + schedule.getCloseTime().format(TIME_FORMATTER));
        }
    }

    public boolean isGymOpenNow() {
        try {
            validateGymIsOpen();
            return true;
        } catch (GymClosedException e) {
            return false;
        }
    }
}
