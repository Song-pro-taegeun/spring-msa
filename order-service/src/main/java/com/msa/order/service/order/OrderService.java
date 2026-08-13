package com.msa.order.service.order;

import com.msa.order.entity.order.Users;
import com.msa.order.repository.order.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final UsersRepository usersRepository;

    public String getMe(){
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        return user.getUserId();
    }
}
