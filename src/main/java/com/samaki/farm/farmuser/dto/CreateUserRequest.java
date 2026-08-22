package com.samaki.farm.farmuser.dto;

/**
 * Admin anaongeza mfanyakazi kwenye shamba lililopo (inahitaji manage_users) -
 * tofauti na SignupRequest ambayo inaunda shamba jipya pamoja na mmiliki wake.
 */
public record CreateUserRequest(String name, String phone, String email, String password,
                                 Integer farmId, Integer roleId) {}
