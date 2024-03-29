package com.fansos.miconvert.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fansos.miconvert.constant.ResultCodeEnum;
import com.fansos.miconvert.model.pojo.UserInfo;
import com.fansos.miconvert.model.result.Result;
import com.fansos.miconvert.service.UserService;
import com.fansos.miconvert.utils.CreateVerifiCodeImage;
import com.fansos.miconvert.utils.JwtHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Diligence
 * @create 2022 - 10 - 23 20:37
 */
@RestController
@CrossOrigin
@RequestMapping("/system")
public class UserController {

	@Autowired
	private UserService systemService;

	@Resource
	private RedisTemplate redisTemplate;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	/**
	 * 用户注册
	 * @param userName
	 * @param password
	 * @param email
	 * @return
	 */
	@PostMapping("/register")
	public Result<?> register(@RequestParam("username") String userName,
	                       @RequestParam("password") String password,
	                       @RequestParam("email") String email) {
		// Todo 邮箱验证


		// 封装新用户
		UserInfo newUser = new UserInfo();
		newUser.setUsername(userName);
		newUser.setPassword(password);
		newUser.setEmail(email);


		// 将注册用户信息写进数据库
		UserInfo saveUser = systemService.saveUser(newUser);


		// 用户的名和用户email转换成一个密文，用token的名称向客户端反馈
		String token = JwtHelper.createToken((long) saveUser.getUserId(), userName, email);
		redisTemplate.opsForValue().set(token, saveUser.getUserId());
		redisTemplate.expire(token, JwtHelper.getTokenExpiration(), TimeUnit.SECONDS);

		return Result.ok(token);
	}

	@PostMapping("/login")
	public Result<?> login(@RequestBody UserInfo loginInfo, HttpServletRequest request) {

		//验证码校验
		//返回与该请求关联的当前 session 会话，或者如果请求没有 session 会话，则创建一个。
		HttpSession session = request.getSession();
		String sessionVerifiCode = (String) session.getAttribute("verifiCode");
		String loginVerifiCode = loginInfo.getVerifiCode();

		// session 中没有验证码，说明验证码过期
		if ("".equals(sessionVerifiCode) || null == sessionVerifiCode) {
			return Result.fail().message("验证码失效，刷新后重试");
		}

		// 用户输入的验证码和session中的验证码不一致，验证码输入错误
		if (!sessionVerifiCode.equalsIgnoreCase(loginVerifiCode)) {
			return Result.fail().message("验证码有误,请重新输入");
		}

		//从session域中移除现有的验证码
		session.removeAttribute("verifiCode");

		//分用户类型进行校验
		Map<Object, Object> map = new LinkedHashMap<>();


		try {
			UserInfo info = systemService.getInfoByName(loginInfo.getUsername());

			if (null != info && info.getPassword().equals(loginInfo.getPassword())) {
				// 用户的类型和用户id转换成一个密文，用token的名称向客户端反馈
				String token = JwtHelper.createToken((long) info.getUserId(), info.getEmail(), info.getUsername());
				redisTemplate.opsForValue().set(token, info.getUserId());
				redisTemplate.opsForValue().getAndExpire(token, JwtHelper.getTokenExpiration(), TimeUnit.SECONDS);
				map.put("token", token);
			} else {
				throw new RuntimeException("用户名或密码错误");
			}
			return Result.ok(map);

		} catch (RuntimeException e) {
			e.printStackTrace();
			return Result.fail().message(e.getMessage());
		}
	}

	/**
	 * 登出
	 * @param request
	 * @return
	 */
	@GetMapping("/logout")
	public Result logout(HttpServletRequest request) {
		String token = request.getHeader("token");
		// Boolean delete = tokenService.deleteRedisKey(token);
		boolean delete = (boolean) redisTemplate.opsForValue().getAndDelete(token);
		if (!delete) {
			return Result.fail("注销失败，请检查是否登录！");
		}
		return Result.ok("注销成功！");
	}

	/**
	 * 修改用户密码
	 * @param token
	 * @param oldPwd
	 * @param newPwd
	 * @return
	 */
	@PostMapping("/updatePwd/{oldPwd}/{newPwd}")
	public Result<?> updatePwd(@RequestHeader("token") String token,
	                        @PathVariable("oldPwd") String oldPwd,
	                        @PathVariable("newPwd") String newPwd) {
		boolean isvalid = JwtHelper.isExpiration(token);
		if (isvalid) {
			//token过期
			return Result.fail().message("token失效!");
		}
		//通过token获取当前登录的用户id
		String email = JwtHelper.getUserEmail(token);
		//通过token获取当前登录的用户名称
		String userName = JwtHelper.getUserName(token);
		// 将明文密码转换为暗文
		oldPwd = passwordEncoder.encode(oldPwd);
		newPwd = passwordEncoder.encode(newPwd);

		QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("username",userName).eq("password", oldPwd);
		UserInfo userInfo = systemService.getOne(queryWrapper);
		if (null != userInfo) {
			userInfo.setPassword(newPwd);
			systemService.saveOrUpdate(userInfo);
		} else {
			return Result.fail().message("原密码输入有误！");
		}
		return Result.ok();
	}


	/**
	 * 解析前端带来的token，获取用户id,name
	 * @param token
	 * @return
	 */
	@GetMapping("/getInfo")
	public Result<?> getInfoByToken(@RequestHeader("token") String token) {
		// 判断token是否有效
		boolean expiration = JwtHelper.isExpiration(token);

		if (expiration) {
			return Result.build(null, ResultCodeEnum.TOKEN_ERROR);
		}
		//从token解释出用户id类型
		Long userId = JwtHelper.getUserId(token);

		Map<String, Object> map = new LinkedHashMap<>();

		map.put("userId", userId);
		return Result.ok(map);
	}

	/**
	 * 1、获取验证码图片，字符串
	 * 2、装入session中
	 * 3、响应给浏览器
	 * @param request
	 * @param response
	 */
	@GetMapping("/getVerifiCodeImage")
	public void getVerifiCodeImage(HttpServletRequest request, HttpServletResponse response) {

		//获取图片
		BufferedImage verifiCodeImage = CreateVerifiCodeImage.getVerifiCodeImage();

		//获取图片上的验证码
		String verifiCode = String.valueOf(CreateVerifiCodeImage.getVerifiCode());

		//将验证码文本放入session域中，为下次验证做准备
		HttpSession session = request.getSession();
		session.setAttribute("verifiCode", verifiCode);

		//将验证码图片响应给浏览器
		try {
			ImageIO.write(verifiCodeImage, "JPEG", response.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

