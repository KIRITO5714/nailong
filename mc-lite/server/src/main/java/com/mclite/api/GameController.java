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
    return new Dtos.Ack(true, null);
  }

  @PostMapping("/worlds/{id}/mine")
  public Dtos.Mutation mine(@PathVariable String id, @Valid @RequestBody Dtos.Mine request) {
    return game.mine(id, request.x(), request.y());
  }

  @PostMapping("/worlds/{id}/place")
  public Dtos.Mutation place(@PathVariable String id, @Valid @RequestBody Dtos.Place request) {
    return game.place(id, request.x(), request.y(), request.item());
  }

  @PostMapping("/worlds/{id}/craft")
  public Dtos.Mutation craft(@PathVariable String id, @Valid @RequestBody Dtos.Craft request) {
    return game.craft(id, request.recipeId());
  }

  @PostMapping("/worlds/{id}/move")
  public Dtos.Ack move(@PathVariable String id, @RequestBody Dtos.Move request) {
    return game.move(id, request.x(), request.y());
  }
}
