package com.fansos.miconvert.utils;

/**
 * @author Diligence
 * @create 2022 - 04 - 14 19:01
 */
import io.jsonwebtoken.*;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;

public class JwtHelper {
    private static long tokenExpiration = 24*60*60*1000;
    private static String tokenSignKey = "123456";

    public static long getTokenExpiration() {
        return 24*60*60*1000;
    }
    //生成token字符串
    public static String createToken(Long userId, String userName, String email) {
        String token = Jwts.builder()

                .setSubject("YYGH-USER")

                .setExpiration(new Date(System.currentTimeMillis() + tokenExpiration))

                .claim("id", userId)
                .claim("email", email)
                .claim("userName", userName)

                .signWith(SignatureAlgorithm.HS512, tokenSignKey)
                .compressWith(CompressionCodecs.GZIP)
                .compact();
        return token;
    }

    //从token字符串获取id
    public static Long getUserId(String token) {
        if(StringUtils.isEmpty(token)) return -1L;
        Jws<Claims> claimsJws
                = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        return (Long) claims.get("id");
    }

    //从token字符串获取email
    public static String getUserEmail(String token) {
        if(StringUtils.isEmpty(token)) return "";
        Jws<Claims> claimsJws
                = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        return (String)claims.get("email");
    }



    //从token字符串获取userName
    public static String getUserName(String token) {
        if(StringUtils.isEmpty(token)) return "";
        Jws<Claims> claimsJws
                = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        return (String)claims.get("userName");
    }

    //判断token是否有效
    public static boolean isExpiration(String token){
        try {
            boolean isExpire = Jwts.parser()
                    .setSigningKey(tokenSignKey)
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration().before(new Date());
            //没有过期，有效，返回false
            return isExpire;
        }catch(Exception e) {
            //过期出现异常，返回true
            return true;
        }
    }


    /**
     * 刷新Token
     * @param token
     * @return
     */
    public String refreshToken(String token) {
        String refreshedToken;
        try {
            final Claims claims = Jwts.parser()
                    .setSigningKey(tokenSignKey)
                    .parseClaimsJws(token)
                    .getBody();
            refreshedToken = JwtHelper.createToken(getUserId(token), getUserEmail(token), getUserName(token));
        } catch (Exception e) {
            refreshedToken = null;
        }
        return refreshedToken;
    }
}
