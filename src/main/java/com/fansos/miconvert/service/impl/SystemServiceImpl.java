package com.fansos.miconvert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fansos.miconvert.mapper.FormatMapper;
import com.fansos.miconvert.mapper.LoginInfoMapper;
import com.fansos.miconvert.model.pojo.Format;
import com.fansos.miconvert.model.pojo.LoginInfo;
import com.fansos.miconvert.service.FileService;
import com.fansos.miconvert.service.SystemService;
import org.springframework.stereotype.Service;

/**
 * @author Diligence
 * @create 2022 - 10 - 23 20:45
 */
@Service
public class SystemServiceImpl extends ServiceImpl<LoginInfoMapper, LoginInfo> implements SystemService {

	/**
	 * 查询数据库，获取用户
	 * @param userName
	 * @return
	 */
	@Override
	public LoginInfo getInfoByName(String userName) {
		QueryWrapper<LoginInfo> wrapper = new QueryWrapper<>();
		wrapper.eq("userName", userName);
		LoginInfo loginInfo = baseMapper.selectOne(wrapper);
		return loginInfo;
	}
}
