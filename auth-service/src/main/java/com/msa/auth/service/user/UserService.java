package com.msa.auth.service.user;

import com.msa.auth.entity.Users;
import com.msa.auth.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UsersRepository userRepository;

    @Transactional
    public List<Users> getUserSchemaUsers() {
        return userRepository.findAll();
    }
}
