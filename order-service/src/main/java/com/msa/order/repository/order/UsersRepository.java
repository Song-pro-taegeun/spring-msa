package com.msa.order.repository.order;

import com.msa.order.entity.order.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository  extends JpaRepository<Users, String> {
}
