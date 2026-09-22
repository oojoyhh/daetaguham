package com.daetaguham.user.application;

import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSignupService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserSignupService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User signup(String name, String phone, String rawPassword) {
		if (userRepository.existsByPhone(phone)) {
			throw new DuplicatePhoneException();
		}

		User user = User.create(phone, passwordEncoder.encode(rawPassword), name.trim());
		try {
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicatePhoneException();
		}
	}
}
