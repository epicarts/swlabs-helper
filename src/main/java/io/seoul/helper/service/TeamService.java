package io.seoul.helper.service;

import io.seoul.helper.config.auth.dto.SessionUser;
import io.seoul.helper.controller.team.dto.*;
import io.seoul.helper.domain.member.Member;
import io.seoul.helper.domain.member.MemberRole;
import io.seoul.helper.domain.project.Project;
import io.seoul.helper.domain.team.Period;
import io.seoul.helper.domain.team.Team;
import io.seoul.helper.domain.team.TeamLocation;
import io.seoul.helper.domain.team.TeamStatus;
import io.seoul.helper.domain.user.User;
import io.seoul.helper.repository.member.MemberRepository;
import io.seoul.helper.repository.project.ProjectRepository;
import io.seoul.helper.repository.team.TeamRepository;
import io.seoul.helper.repository.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class TeamService {
    private final UserRepository userRepo;
    private final TeamRepository teamRepo;
    private final MemberRepository memberRepo;
    private final ProjectRepository projectRepo;
    private final MailSenderService mailSenderService;

    @Transactional
    public TeamResponseDto createNewTeam(SessionUser currentUser, TeamCreateRequestDto requestDto) throws Exception {
        if (requestDto.getMemberRole() == MemberRole.MENTOR) {
            return createNewTeamMentor(currentUser, requestDto);
        }
        if (requestDto.getMemberRole() == MemberRole.MENTEE) {
            return createNewTeamMentee(currentUser, requestDto);
        }
        throw new IllegalArgumentException("MemberRole is null.");
    }

    @Transactional
    public TeamResponseDto createNewTeamMentee(SessionUser currentUser, TeamCreateRequestDto requestDto) throws Exception {
        User user = findUser(currentUser);
        Project project = findProject(requestDto.getProjectId());
        Period period = Period.builder()
                .startTime(requestDto.getStartTime())
                .endTime(requestDto.getEndTime())
                .build();
        if (!period.isValid())
            throw new IllegalArgumentException("Invalid Time");
        Team team = requestDto.toEntity(project, TeamStatus.WAITING);
        team = teamRepo.save(team);
        Member member = Member.builder()
                .team(team)
                .user(user)
                .role(MemberRole.MENTEE)
                .creator(true)
                .build();
        memberRepo.save(member);
        return new TeamResponseDto(team);
    }

    @Transactional
    public TeamResponseDto createNewTeamMentor(SessionUser currentUser, TeamCreateRequestDto requestDto) throws Exception {
        User user = findUser(currentUser);
        Project project = findProject(requestDto.getProjectId());
        Period period = Period.builder()
                .startTime(requestDto.getStartTime())
                .endTime(requestDto.getEndTime())
                .build();
        if (!period.isValid())
            throw new IllegalArgumentException("Invalid Time");
        Team team = requestDto.toEntity(project, TeamStatus.READY);
        team = teamRepo.save(team);
        Member member = Member.builder()
                .team(team)
                .user(user)
                .role(MemberRole.MENTOR)
                .creator(true)
                .build();
        memberRepo.save(member);
        return new TeamResponseDto(team);
    }

    @Transactional
    public TeamResponseDto updateTeamByMentor(SessionUser currentUser, Long teamId, TeamUpdateRequestDto requestDto) throws Exception {
        User user = findUser(currentUser);
        Team team = findTeam(teamId);
        Project project = findProject(requestDto.getProjectId());
        if (memberRepo.findMemberByTeamAndUser(team, user).isPresent())
            throw new Exception("Not valid member");
        Period period = Period.builder()
                .startTime(requestDto.getStartTime())
                .endTime(requestDto.getEndTime())
                .build();
        if (!period.isValid() || !team.getPeriod().isInRanged(period))
            throw new IllegalArgumentException("Invalid Time");
        team.updateTeam(period, requestDto.getMaxMemberCount(), requestDto.getLocation(), project);
        team.joinTeam();
        team = teamRepo.save(team);
        memberRepo.save(Member.builder()
                .team(team)
                .user(user)
                .role(MemberRole.MENTOR)
                .creator(false)
                .build());
        List<Member> members = team.getMembers();
        for (Member member : members) {
            if (member.getCreator())
                mailSenderService.sendMatchMail(member.getUser(), team);
        }
        return new TeamResponseDto(team);
    }

    @Transactional
    public List<TeamResponseDto> updateTeamsLessThanCurrentTime() throws Exception {
        LocalDateTime currentTime = LocalDateTime.now();
        List<Team> teams = teamRepo.findTeamsByStatusNotAndEndTimeLessThan(TeamStatus.END, currentTime);

        if (teams.isEmpty()) {
            throw new EntityNotFoundException("Nothing to change teams");
        }
        for (Team team : teams) {
            team.updateTeamEnd();
        }
        teams = teamRepo.saveAll(teams);

        return teams.stream().map(team -> new TeamResponseDto(team))
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinTeam(SessionUser sessionUser, Long id) throws Exception {
        User user = findUser(sessionUser);
        Team team = findTeam(id);
        if (memberRepo.findMemberByTeamAndUser(team, user).isPresent())
            throw new Exception("Already joined");
        if (team.getStatus() != TeamStatus.READY)
            throw new Exception("This Team is not ready");
        if (team.getCurrentMemberCount() >= team.getMaxMemberCount())
            throw new Exception("member is full");
        else {
            team.joinTeam();
            teamRepo.save(team);
        }

        Member member = Member.builder()
                .team(team)
                .user(user)
                .creator(false)
                .role(MemberRole.MENTEE)
                .build();
        memberRepo.save(member);
    }

    @Transactional
    public void outTeam(SessionUser sessionUser, Long id) throws Exception {
        User user = findUser(sessionUser);
        Team team = findTeam(id);
        Member member = memberRepo.findMemberByTeamAndUser(team, user)
                .orElseThrow(() -> new Exception("Not this team member"));
        if (team.getStatus() == TeamStatus.END)
            throw new Exception("This team is end status");
        if (team.getStatus() == TeamStatus.WAITING)
            throw new Exception("This team is waiting status");
        if (isCreator(member))
            throw new Exception("Creator can not leave the team");
        if (isMentor(member)) {
            throw new Exception("Mentor can not leave the team");
        }
        team.outTeam();
        teamRepo.save(team);
        memberRepo.delete(member);
    }


    public void endTeam(SessionUser sessionUser, Long id) throws Exception {
        User user = findUser(sessionUser);
        Team team = findTeam(id);
        memberRepo.findMemberByTeamAndUserAndRole(team, user, MemberRole.MENTOR)
                .orElseThrow(() -> new Exception("Not this team mentor"));
        if (team.getStatus() == TeamStatus.END)
            throw new Exception("Already end status");
        else {
            team.updateTeamEnd();
            teamRepo.save(team);
        }

        List<Member> members = team.getMembers();
        for (Member member : members) {
            mailSenderService.sendEndMail(member.getUser(), team);
        }
    }

    private boolean isCreator(Member member) {
        return member.getCreator();
    }

    private boolean isMentor(Member member) {
        return member.getRole() == MemberRole.MENTOR;
    }

    @Transactional
    public void deleteTeam(SessionUser sessionUser, Long id) throws Exception {
        User user = findUser(sessionUser);
        Team team = findTeam(id);
        Member member = memberRepo.findMemberByTeamAndUser(team, user)
                .orElseThrow(() -> new Exception("Not this team member"));
        if (team.getStatus() != TeamStatus.WAITING)
            throw new Exception("This Team is already Matched!");
        memberRepo.delete(member);
        teamRepo.delete(team);
    }

    private Pageable toPageable(int offset, int limit, String sort) throws Exception {
        Pageable pageable;
        try {
            if (sort.contains(",")) {
                String[] sortOption = sort.split(",");
                pageable = PageRequest.of(
                        offset, limit,
                        sortOption[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                        sortOption[0]);
            } else {
                pageable = PageRequest.of(
                        offset, limit, Sort.Direction.DESC, sort);
            }
        } catch (Exception e) {
            throw new Exception("failed to Pageable");
        }
        return pageable;
    }

    @Transactional
    public Page<TeamResponseDto> findTeams(TeamListRequestDto requestDto) {
        Page<Team> teams;
        try {
            Pageable pageable = toPageable(requestDto.getOffset(), requestDto.getLimit(), requestDto.getSort());

            if (requestDto.getNickname() != null) {
                List<Long> teamIds = findTeamIdsByNickname(requestDto.getNickname(), requestDto.isCreateor(), requestDto.getMemberRole());

                teams = teamRepo.findTeamsByTeamIdIn(
                        requestDto.getStartTimePrevious(), requestDto.getEndTimePrevious(), requestDto.getStatus(),
                        requestDto.getLocation(), teamIds, pageable);
            } else if (requestDto.getExcludeNickname() != null) {
                List<Long> teamIds = findTeamIdsByNickname(requestDto.getExcludeNickname(), requestDto.isCreateor(), requestDto.getMemberRole());

                if (teamIds.isEmpty()) {
                    teams = teamRepo.findTeamsByQueryParameters(
                            requestDto.getStartTimePrevious(), requestDto.getEndTimePrevious(), requestDto.getStatus(),
                            requestDto.getLocation(), pageable);
                } else {
                    teams = teamRepo.findTeamsByTeamIdNotIn(
                            requestDto.getStartTimePrevious(), requestDto.getEndTimePrevious(), requestDto.getStatus(),
                            requestDto.getLocation(), teamIds, pageable);
                }

            } else {
                teams = teamRepo.findTeamsByQueryParameters(
                        requestDto.getStartTimePrevious(), requestDto.getEndTimePrevious(), requestDto.getStatus(),
                        requestDto.getLocation(), pageable);
            }
        } catch (Exception e) {
            log.error("failed to find teams : " + e.getMessage() + "\n\n" + e.getCause());
            return null;
        }
        return teams.map(team -> new TeamResponseDto(team));
    }

    private List<Long> findTeamIdsByNickname(String nickName, boolean isCreator, MemberRole memberRole) {
        User user = userRepo.findUserByNickname(nickName).get();
        List<Member> members;

        if (isCreator)
            members = memberRepo.findMembersByUserAndCreatorAndRole(user, true, memberRole);
        else
            members = memberRepo.findMembersByUserAndRole(user, memberRole);


        return members.stream()
                .map(m -> m.getTeam().getId())
                .collect(Collectors.toList());
    }

    private User findUser(SessionUser currentUser) throws Exception {
        if (currentUser == null) throw new Exception("not login");
        return userRepo.findUserByNickname(currentUser.getNickname())
                .orElseThrow(() -> new EntityNotFoundException("invalid user"));
    }

    private User findUser(String nickname) throws Exception {
        if (nickname == null) throw new Exception("not login");
        return userRepo.findUserByNickname(nickname)
                .orElseThrow(() -> new EntityNotFoundException("invalid user"));
    }

    private Team findTeam(Long teamId) throws EntityNotFoundException {
        return teamRepo.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not exist!"));
    }

    private Project findProject(Long projectId) throws EntityNotFoundException {
        return projectRepo.findById(projectId).
                orElseThrow(() -> new EntityNotFoundException("invalid project"));
    }

    private Project findProject(String projectName) throws EntityNotFoundException {
        return projectRepo.findProjectByName(projectName).
                orElseThrow(() -> new EntityNotFoundException("invalid project"));
    }

    public List<TeamLocationDto> findAllLocation() {
        return Arrays.stream(TeamLocation.values()).map((o) -> {
            return TeamLocationDto.builder()
                    .id(o.getId())
                    .code(o.name())
                    .name(o.getName())
                    .build();
        }).collect(Collectors.toList());
    }
}
