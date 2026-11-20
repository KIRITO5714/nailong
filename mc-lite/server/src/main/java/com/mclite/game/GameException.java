package com.mclite.game;

// 玩法拒绝都走这个异常：code 给前端做分支，message 是直接能显示给玩家看的中文。
public class GameException extends RuntimeException {

  private final String code;

  public GameException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
