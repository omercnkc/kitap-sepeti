package com.kitapsepeti.user.service;

import java.util.List;
import java.util.UUID;

import com.kitapsepeti.common.error.ResourceNotFoundException;
import com.kitapsepeti.user.dto.request.CreateAddressRequest;
import com.kitapsepeti.user.dto.request.UpdateAddressRequest;
import com.kitapsepeti.user.dto.response.AddressResponse;
import com.kitapsepeti.user.entity.Address;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.exception.DefaultAddressRequiredException;
import com.kitapsepeti.user.mapper.AddressMapper;
import com.kitapsepeti.user.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oturum sahibinin adresleri. Kural: adresi olan kullanıcının tam olarak bir varsayılan adresi vardır
 * (en fazla bir tanesini DB'deki {@code uk_addresses_default_owner} de garanti eder).
 * Başkasının adresi, var olmayan adresle aynı şekilde 404 döner (varlığı sızdırılmaz).
 */
@Service
@Transactional
public class AddressService {

	private final AddressRepository addressRepository;

	private final UserService userService;

	public AddressService(AddressRepository addressRepository, UserService userService) {
		this.addressRepository = addressRepository;
		this.userService = userService;
	}

	@Transactional(readOnly = true)
	public List<AddressResponse> list(UUID userId) {
		userService.requireActiveUser(userId);
		return addressRepository.findAllOrdered(userId).stream().map(AddressMapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public AddressResponse get(UUID userId, UUID addressId) {
		userService.requireActiveUser(userId);
		return AddressMapper.toResponse(find(userId, addressId));
	}

	/** İlk adres, istekten bağımsız olarak varsayılan olur. */
	public AddressResponse create(UUID userId, CreateAddressRequest request) {
		User user = userService.requireActiveUser(userId);
		boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || !addressRepository.existsByUserId(userId);
		if (makeDefault) {
			addressRepository.clearDefault(userId);
		}
		Address address = AddressMapper.toEntity(user, request);
		address.setDefault(makeDefault);
		return AddressMapper.toResponse(addressRepository.saveAndFlush(address));
	}

	public AddressResponse update(UUID userId, UUID addressId, UpdateAddressRequest request) {
		userService.requireActiveUser(userId);
		Address address = find(userId, addressId);
		if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
			// Zaten varsayılansa temizleme yapılmaz: toplu UPDATE bu satırı DB'de false yapar ama yüklü
			// entity true kalır; Hibernate değişiklik görmez ve adres sessizce varsayılanlıktan çıkarılırdı.
			addressRepository.clearDefault(userId);
			address.setDefault(true);
		}
		else if (Boolean.FALSE.equals(request.isDefault()) && address.isDefault()) {
			throw new DefaultAddressRequiredException();
		}
		AddressMapper.applyUpdate(address, request);
		addressRepository.flush();
		return AddressMapper.toResponse(address);
	}

	/** Silinen varsayılansa, kalan en yeni adres varsayılan olur. */
	public void delete(UUID userId, UUID addressId) {
		userService.requireActiveUser(userId);
		Address address = find(userId, addressId);
		boolean wasDefault = address.isDefault();
		addressRepository.delete(address);
		// Hibernate flush'ta update'leri delete'lerden önce çalıştırır; silme, yeni varsayılan
		// işaretlenmeden yazılmalı, yoksa iki satır aynı anda varsayılan olur ve UNIQUE ihlal edilir.
		// Sorgunun otomatik flush'ına güvenmek yerine sıra burada açıkça sabitlenir.
		addressRepository.flush();
		if (wasDefault) {
			addressRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
				.ifPresent(next -> next.setDefault(true));
		}
	}

	private Address find(UUID userId, UUID addressId) {
		return addressRepository.findByIdAndUserId(addressId, userId)
			.orElseThrow(() -> new ResourceNotFoundException("Address not found."));
	}

}
