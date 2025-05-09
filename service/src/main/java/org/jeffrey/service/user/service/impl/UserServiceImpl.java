package org.jeffrey.service.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeffrey.api.dto.user.UserUpdateDTO;
import org.jeffrey.api.vo.User.UserVO;
import org.jeffrey.service.file.FileService;
import org.jeffrey.service.user.repository.entity.UserDO;
import org.jeffrey.service.user.repository.mapper.UserMapper;
import org.jeffrey.service.user.service.UserService;
import org.jeffrey.service.security.CustomUserDetails;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    public UserServiceImpl(@Lazy PasswordEncoder passwordEncoder, FileService fileService) {
        this.passwordEncoder = passwordEncoder;
        this.fileService = fileService;
    }

    @Override
    public List<UserDO> getAllUsers() {
        return this.list();
    }

    @Override
    public Optional<UserDO> getUserByUsername(String username) {
        return baseMapper.findByUsername(username).stream().findFirst();
    }

    @Override
    public boolean registerUser(String username, String password) {
        // Check if username already exists
        if (getUserByUsername(username).isPresent()) {
            return false;
        }

        // Create new user
        UserDO user = new UserDO();
        user.setUsername(username);
        // Encode password before saving
        user.setPassword(passwordEncoder.encode(password));
        user.setIsAdmin(false);

        // Save user to database
        return save(user);
    }

    @Override
    public List<UserDO> getUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return listByIds(userIds);
    }

    /**
     * 更新当前登录用户的资料
     */
    @Override
    public UserVO updateUserProfile(UserUpdateDTO dto) {
        Long currentUserId = getCurrentUserId(); // 获取当前用户ID
        UserDO user = this.getById(currentUserId);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 更新字段（可扩展为字段校验）
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setAvatarUrl(dto.getAvatarUrl());
        // 处理bio字段，需要在UserDO中添加此字段
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
        }

        this.updateById(user);

        // 返回展示对象
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * 从Spring Security上下文中获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return userDetails.getUserId();
        }
        // 如果未获取到认证信息，抛出异常
        throw new RuntimeException("用户未登录");
    }

    /**
     * 更新用户头像
     * @param avatar 头像文件
     * @return 头像URL
     */
    @Override
    public String updateUserAvatar(MultipartFile avatar) {
        if (avatar.isEmpty()) {
            throw new RuntimeException("上传的头像文件为空");
        }

        // 获取当前登录用户
        Long currentUserId = getCurrentUserId();
        UserDO user = this.getById(currentUserId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 使用FileService上传头像
        String[] allowedTypes = {"image/jpeg", "image/png"};
        long maxSize = 2 * 1024 * 1024; // 2MB
        String avatarUrl = fileService.uploadFile(avatar, "avatars", allowedTypes, maxSize);

            // 更新用户头像URL
            user.setAvatarUrl(avatarUrl);
            this.updateById(user);

            return avatarUrl;
    }
}
