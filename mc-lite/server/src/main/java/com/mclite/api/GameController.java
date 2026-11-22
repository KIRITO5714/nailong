package com.mclite.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.mclite.content.ContentRegistry;
import com.mclite.game.GameService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameController {

  private final GameService game;
  private final ContentRegistry content;

  public GameController(GameService game, ContentRegistry content) {
    this.game = game;
    this.content = content;
  }

  @GetMapping("/content")
  public JsonNode content() {
    return content.raw();
  }

  @GetMapping("/worlds")
  public List<Dtos.WorldSummary> list() {
    return game.list();
  }

  @PostMapping("/worlds")
  public Dtos.WorldView create(@RequestBody(required = false) Dtos.CreateWorld request) {
    return game.create(request == null ? null : request.name(), request == null ? null : request.seed());
  }

  @GetMapping("/worlds/{id}")
  public Dtos.WorldView load(@PathVariable String id) {
    return game.load(id);
  }

  @DeleteMapping("/worlds/{id}")
  public Dtos.Ack delete(@PathVariable String id) {
    game.delete(id);
    return new Dtos.Ack(true, null, null);
  }

  // 客户端判定挥砍命中后上报被杀的怪，服务端结算掉落与击杀数。
  @PostMapping("/worlds/{id}/loot")
  public Dtos.Mutation loot(@PathVariable String id, @Valid @RequestBody Dtos.Loot request) {
    return game.loot(id, request.mob());
  }

  @PostMapping("/worlds/{id}/craft")
  public Dtos.Mutation craft(@PathVariable String id, @Valid @RequestBody Dtos.Craft request) {
    return game.craft(id, request.recipeId());
  }

  @GetMapping("/worlds/{id}/bounties")
  public Dtos.BountiesView bounties(@PathVariable String id) {
    return game.bounties(id);
  }

  @PostMapping("/worlds/{id}/deliver")
  public Dtos.Mutation deliver(@PathVariable String id, @Valid @RequestBody Dtos.Deliver request) {
    return game.deliver(id, request.bountyId());
  }

  @PostMapping("/worlds/{id}/move")
  public Dtos.Ack move(@PathVariable String id, @RequestBody Dtos.Move request) {
    return game.move(id, request.x(), request.y(), request.hp());
  }
}
