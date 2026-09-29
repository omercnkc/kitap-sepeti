package com.kitapsepeti.user.service;

import java.util.UUID;

import com.kitapsepeti.user.dto.request.UpdateProfileRequest;
import com.kitapsepeti.user.dto.response.UserResponse;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.entity.UserStatus;
import com.kitapsepeti.user.exception.AccountSuspendedException;
import com.kitapsepeti.user.exception.UnauthorizedException;
import com.kitapsepeti.user.mapper.UserMapper;
import com.kitapsepeti.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Oturum sahibinin profili. */
@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse getProfile(UUID userId) {
		return UserMapper.toResponse(requireActiveUser(userId));
	}

	@Transactional
	public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		User user = requireActiveUser(userId);
		UserMapper.applyUpdate(user, request);
		return UserMapper.toResponse(user);
	}

	/**
	 * Token'daki kullanıcıyı yükler. Token imzası geçerli olsa da kullanıcı silinmişse 401, askıya
	 * alınmışsa 403 döner; token süresi dolana kadar bekletilmez. Çağıranın transaction'ında çalışır.
	 */
	public User requireActiveUser(UUID userId) {
		User user = userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new AccountSuspendedException();
		}
		return user;
	}

}
