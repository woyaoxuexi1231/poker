package com.poker.controller;

import com.poker.util.AvatarPreset;
import com.poker.dto.RoomDTO;
import com.poker.entity.User;
import com.poker.service.RoomService;
import com.poker.service.RoomQueryService;
import com.poker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final RoomService roomService;
    private final RoomQueryService roomQueryService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

@GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("avatars", AvatarPreset.AVATARS);
        return "register";
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String nickname,
                             @RequestParam(defaultValue = "") String avatar,
                             RedirectAttributes redirectAttributes) {
        try {
            if (userService.findByUsername(username) != null) {
                redirectAttributes.addFlashAttribute("error", "用户名已存在");
                return "redirect:/register";
            }
            userService.register(username, passwordEncoder.encode(password), nickname, avatar);
            redirectAttributes.addFlashAttribute("success", "注册成功，请登录");
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/rankings")
    public String rankings() {
        return "rankings";
    }

    @GetMapping("/")
    public String index(Model model, Authentication auth) {
        if (auth == null) return "redirect:/login";
        User user = userService.findByUsername(auth.getName());
        if (user == null) return "redirect:/login";

        List<RoomDTO> myRooms = roomQueryService.getMyRooms(user.getId());
        List<RoomDTO> availableRooms = roomQueryService.getAvailableRooms(user.getId());

        model.addAttribute("avatar", user.getAvatar());
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("userId", user.getId());
        model.addAttribute("myRooms", myRooms);
        model.addAttribute("availableRooms", availableRooms);
        return "index";
    }

    @PostMapping("/room/create")
    public String createRoom(@RequestParam(required = false) String password,
                            Authentication auth, 
                            RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findByUsername(auth.getName());
            String roomId = roomService.createRoom(user.getId(), password);
            return "redirect:/room/" + roomId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/room/join")
    public String joinRoom(@RequestParam String roomId,
                          @RequestParam(required = false) String password,
                           Authentication auth,
                           RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findByUsername(auth.getName());
            roomService.joinRoom(roomId, user.getId(), password);
            return "redirect:/room/" + roomId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/room/{roomId}")
    public String room(@PathVariable String roomId, Model model, Authentication auth) {
        User user = userService.findByUsername(auth.getName());
        RoomDTO roomDTO = roomQueryService.getRoomData(roomId);
        if (roomDTO == null) {
            return "redirect:/";
        }
        // 已解散的房间不允许进入
        if ("DISSOLVED".equals(roomDTO.getStatus())) {
            return "redirect:/";
        }
        // 检查玩家是否是房间的活跃成员（不再调用 joinRoom 避免重新激活已退出的玩家）
        if (!roomService.isPlayerActive(roomId, user.getId())) {
            return "redirect:/";
        }
        model.addAttribute("roomId", roomId);
        model.addAttribute("userId", user.getId());
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("avatar", user.getAvatar());
        return "room";
    }
}
