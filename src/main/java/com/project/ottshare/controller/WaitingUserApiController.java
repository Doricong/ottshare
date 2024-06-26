package com.project.ottshare.controller;

import com.project.ottshare.dto.ottShareRoomDto.OttShareRoomResponse;
import com.project.ottshare.dto.ottShareRoomDto.OttSharingRoomRequest;
import com.project.ottshare.dto.waitingUserDto.WaitingUserRequest;
import com.project.ottshare.dto.waitingUserDto.WaitingUserResponse;
import com.project.ottshare.entity.SharingUser;
import com.project.ottshare.exception.OttLeaderNotFoundException;
import com.project.ottshare.exception.OttNonLeaderNotFoundException;
import com.project.ottshare.exception.UserNotFoundException;
import com.project.ottshare.service.ottShareRoom.OttShareRoomService;
import com.project.ottshare.service.sharingUser.SharingUserService;
import com.project.ottshare.service.waitingUser.WaitingUserService;
import com.project.ottshare.validation.ValidationSequence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/waitingUser")
@Slf4j
public class WaitingUserApiController {

    private final WaitingUserService waitingUserService;
    private final OttShareRoomService ottShareRoomService;
    private final SharingUserService sharingUserService;

    /**
     * user 저장
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveWaitingUser(@Validated(ValidationSequence.class) @RequestBody WaitingUserRequest dto) {
        // 사용자 정보 저장
        log.info("Saving user data: {}", dto.getOtt());
        waitingUserService.save(dto);

        try {
            List<WaitingUserResponse> member = waitingUserService.getNonLeaderByOtt(dto.getOtt());
            WaitingUserResponse leader = waitingUserService.getLeaderByOtt(dto.getOtt());

            member.add(leader); // 리더 정보를 리스트에 추가

            // 인원 수가 충분하면 자동 매칭 대기방에서 사용자 삭제 및 방을 생성
            waitingUserService.deleteUsers(member);

            List<SharingUser> sharingUsers = sharingUserService.prepareSharingUsers(member);
            String ottId = leader.getOttId();
            String ottPassword = leader.getOttPassword();

            OttSharingRoomRequest ottSharingRoomRequest = new OttSharingRoomRequest(sharingUsers, dto.getOtt(), ottId, ottPassword);

            Long savedOttShareRoomId = ottShareRoomService.save(ottSharingRoomRequest);// 방 생성 로직
            OttShareRoomResponse ottShareRoom = ottShareRoomService.getOttShareRoom(savedOttShareRoomId);

            sharingUserService.associateRoomWithSharingUsers(sharingUsers, ottShareRoom);

            log.info("Room created successfully for OTT service: {}", dto.getOtt());

            return ResponseEntity.ok("Room created successfully.");
        } catch (OttNonLeaderNotFoundException e) {
            return ResponseEntity.ok("팀원 아직 다 안 모임");
        } catch (OttLeaderNotFoundException e) {
            return ResponseEntity.ok("방장 아직 없음");
        }
    }


    /**
     * user 삭제
     */
    @DeleteMapping("/matchings/{matchingId}")
    public ResponseEntity<String> deleteWaitingUser(@PathVariable("matchingId") Long matchingId) {
        //user 삭제
        waitingUserService.deleteUser(matchingId);

        return ResponseEntity.ok("User deleted successfully");
    }

    /**
     * userId로 user 조회
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Long> findWaitingUser(@PathVariable("userId") Long userId) {
        Long waitingUserId = waitingUserService.getWaitingUserIdByUserId(userId)
                .orElse(0L);
        return ResponseEntity.ok(waitingUserId);
    }

}
