/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pulsar.manager.controller;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.pulsar.manager.entity.UserInfoEntity;
import org.apache.pulsar.manager.entity.UsersRepository;
import org.apache.pulsar.manager.service.JwtService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.casbin.casdoor.service.CasdoorAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * User login and logout rest api.
 */
@Slf4j
@RequestMapping(value = "/pulsar-manager")
@Api(description = "Support user login and logout.")
@Validated
@RestController
public class LoginController {

    private final JwtService jwtService;

    @Autowired
    public LoginController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CasdoorAuthService casdoorAuthService;

    @Value("${pulsar-manager.account}")
    private String account;

    @Value("${pulsar-manager.password}")
    private String password;

    @ApiOperation(value = "Login pulsar manager")
    @ApiResponses({
            @ApiResponse(code = 200, message = "ok"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @RequestMapping(value = "/login", method =  RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body) {
        String userAccount = body.get("username");
        String userPassword = body.get("password");
        Map<String, Object> result = Maps.newHashMap();
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        Optional<UserInfoEntity> userInfoEntityOptional = usersRepository.findByUserName(userAccount);
        if (!userInfoEntityOptional.isPresent()) {
            result.put("error", "The user is not exist");
            return ResponseEntity.ok(result);
        }
        UserInfoEntity userInfoEntity = userInfoEntityOptional.get();
        String password = DigestUtils.sha256Hex(userPassword);
        if (!password.equals(userInfoEntity.getPassword())) {
            result.put("error", "The user name or password not incorrect");
            return ResponseEntity.ok(result);
        }
        String token = jwtService.toToken(userAccount + password + System.currentTimeMillis());
        userInfoEntity.setAccessToken(token);
        usersRepository.update(userInfoEntity);
        result.put("login", "success");
        headers.add("token", token);
        headers.add("username", userAccount);

        jwtService.setToken(request.getSession().getId(), token);
        headers.add("role", "super");
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    @ApiOperation(value = "Logout pulsar manager")
    @ApiResponses({
            @ApiResponse(code = 200, message = "ok"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @RequestMapping(value = "/logout", method =  RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> result = Maps.newHashMap();
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String username = request.getHeader("username");
        usersRepository.findByUserName(username);
        Optional<UserInfoEntity> userInfoEntityOptional = usersRepository.findByUserName(username);
        if (!userInfoEntityOptional.isPresent()) {
            result.put("login", "The user is not exist");
            return ResponseEntity.ok(result);
        }
        UserInfoEntity userInfoEntity = userInfoEntityOptional.get();
        userInfoEntity.setAccessToken("");
        usersRepository.update(userInfoEntity);
        return ResponseEntity.ok(result);
    }

    @ApiOperation(value = "Logout pulsar manager")
    @ApiResponses({
            @ApiResponse(code = 200, message = "ok"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @RequestMapping(value = "/casdoor", method =  RequestMethod.POST)
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody Map<String, String> body) {
        Map<String, Object> result = Maps.newHashMap();
        String code = body.get("code");
        String state = body.get("state");
        String token = casdoorAuthService.getOAuthToken(code, state);
        if(!token.startsWith("error")){
            result.put("error", token.substring(7));
            return ResponseEntity.ok(result);
        }
        result.put("login", "success");
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String jwtToken = jwtService.toToken(account + "-" + password);
        jwtService.setToken(request.getSession().getId(), jwtToken);
        headers.add("token", jwtToken);
        headers.add("tenant","pulsar");
        headers.add("username", "pulsar");
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }
}
