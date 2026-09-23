package com.daetaguham.user.application;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.daetaguham.user.domain.Availability;
import com.daetaguham.user.domain.AvailabilityRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

	private final UserRepository userRepository;
	private final AvailabilityRepository availabilityRepository;

	public AvailabilityService(
			UserRepository userRepository,
			AvailabilityRepository availabilityRepository
	) {
		this.userRepository = userRepository;
		this.availabilityRepository = availabilityRepository;
	}

	@Transactional(readOnly = true)
	public List<Availability> findMine(Long userId) {
		return availabilityRepository.findAllByUser_IdOrderByDayOfWeekAsc(userId);
	}

	@Transactional
	public List<Availability> replaceMine(Long userId, List<AvailabilityCommand> commands) {
		validate(commands);
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		availabilityRepository.deleteAllByUser_Id(userId);
		availabilityRepository.flush();
		List<Availability> replacements = commands.stream()
				.map(command -> Availability.create(
						user,
						command.dayOfWeek(),
						command.startTime(),
						command.endTime()
				))
				.toList();
		availabilityRepository.saveAll(replacements);
		return availabilityRepository.findAllByUser_IdOrderByDayOfWeekAsc(userId);
	}

	private void validate(List<AvailabilityCommand> commands) {
		if (commands.size() > 7) {
			throw new InvalidAvailabilityException("근무 가능 시간은 요일별로 하나씩만 저장할 수 있어요.");
		}
		Set<Integer> days = new HashSet<>();
		for (AvailabilityCommand command : commands) {
			if (command.dayOfWeek() < 0 || command.dayOfWeek() > 6) {
				throw new InvalidAvailabilityException("요일은 0(일)부터 6(토)까지로 입력해 주세요.");
			}
			if (!days.add(command.dayOfWeek())) {
				throw new InvalidAvailabilityException("같은 요일을 두 번 저장할 수 없어요.");
			}
			if (!command.endTime().isAfter(command.startTime())) {
				throw new InvalidAvailabilityException("근무 가능 종료 시간은 시작 시간보다 늦어야 해요.");
			}
		}
	}

	public record AvailabilityCommand(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
	}
}
