package com.beotkkotthon.areyousleeping.service;

import com.beotkkotthon.areyousleeping.domain.Report;
import com.beotkkotthon.areyousleeping.domain.User;
import com.beotkkotthon.areyousleeping.domain.UserTeam;
import com.beotkkotthon.areyousleeping.dto.request.UserReportRequestDto;
import com.beotkkotthon.areyousleeping.dto.response.UserReportResponseDto;
import com.beotkkotthon.areyousleeping.exception.CommonException;
import com.beotkkotthon.areyousleeping.exception.ErrorCode;
import com.beotkkotthon.areyousleeping.repository.ReportRepository;
import com.beotkkotthon.areyousleeping.repository.UserRepository;
import com.beotkkotthon.areyousleeping.repository.UserTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final UserTeamService userTeamService;
    private final UserTeamRepository userTeamRepository;


    @Transactional
    public UserReportResponseDto reportUser(Long userId, UserReportRequestDto userReportRequestDto){

        // 신고자 조회
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND_USER));

        //신고할 유저 조회
        User reportedUser = userRepository.findById(userReportRequestDto.reportedUserId())
                .orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND_USER));

        //신고자와 신고될 유저가 동일한 경우
        if (reporter.equals(reportedUser)){
            throw new CommonException(ErrorCode.INVALID_USER_REPORT);
        }

        //특정 유저를 이전에 신고했다면 예외 발생
        if (reportRepository.existsByReporterAndReportedUser(reporter, reportedUser)){
            throw new CommonException(ErrorCode.ALREADY_REPORT);
        }

        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reportContent(userReportRequestDto.content())
                .createdAt(LocalDateTime.now())
                .build();

        reportRepository.save(report);


        // 유저의 신고를 10명이상에게 받을 시 user의 isBlocked 필드를 true로 설정
        List<Report> reports = reportRepository.findByReportedUser(reportedUser);
        if(reports.size()>=10){
            reportedUser.setReported(true);
            userRepository.save(reportedUser);

            // 신고받은 유저가 속한 팀 정보를 조회 -> 팀 퇴출 수행
            Optional<UserTeam> userTeamOptional = userTeamRepository.findFirstByUserIdOrderByIdDesc(reportedUser.getId());
            if (userTeamOptional.isPresent()) {
                UserTeam userTeam = userTeamOptional.get();
                userTeamService.leaveTeam(userTeam.getTeam().getId(), reportedUser.getId());
            }
        }

        return UserReportResponseDto.fromEntity(report);
    }


}
