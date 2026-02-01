# viewer/viewer.py
import pygame
from game_adapter import DuelGameAdapter
from renderer.board_renderer import BoardRenderer

CLEAR_FRAMES = 5 

def main():
    pygame.init()

    TOP_K = 5
    speed = 1
    paused = False

    game = DuelGameAdapter(seed=1, top_k=TOP_K)
    renderer = BoardRenderer(cell_size=28, top_k=TOP_K)

    clock = pygame.time.Clock()
    running = True
    winner_text = ""

    clear_timer = 0

    while running:
        for e in pygame.event.get():
            if e.type == pygame.QUIT:
                running = False
            elif e.type == pygame.KEYDOWN:
                print(e)
                if e.key == pygame.K_ESCAPE:
                    running = False
                elif e.key == pygame.K_SPACE:
                    paused = not paused
                elif e.key == pygame.K_n and paused and not game.is_done():
                    game.step()
                elif e.key == pygame.K_UP or e.key == pygame.K_EQUALS:
                    speed = min(10, speed+0.5)
                elif e.key == pygame.K_DOWN:
                    speed = max(0.5, speed-0.5)

        if not paused and not game.is_done():
            game.step()
            # 消去があったら 2f フラッシュ
            if (game.debug1.lines_cleared > 0) or (game.debug2.lines_cleared > 0):
                clear_timer = CLEAR_FRAMES

        if game.is_done():
            winner_text = str(game.get_winner()).split(".")[-1]

        # 表示盤面の切替
        board1 = game.get_board1()
        board2 = game.get_board2()

        if clear_timer > 0:
            clear_flash = True
            clear_timer -= 1
        else:
            clear_flash = False
        
        # viewer/viewer.py

        # game.state1.bag が PieceBag クラスのインスタンスである場合
        next1 = []
        next2 = []

        if hasattr(game.state1, 'bag'):
            # C++ の peek メソッドを呼び出して Python のリストを取得
            next1 = list(game.state1.bag.peek(5)) 

        if hasattr(game.state2, 'bag'):
            next2 = list(game.state2.bag.peek(5))

                
        renderer.draw_duel(
            board1=board1,
            board2=board2,
            info1=game.get_info1(),
            info2=game.get_info2(),
            topk1=game.get_topk1(),
            topk2=game.get_topk2(),
            landing1=game.get_last_landing1(),
            landing2=game.get_last_landing2(),
            board_before1=game.debug1.board_before_clear,
            board_before2=game.debug2.board_before_clear,
            clear_flash=clear_flash,
            paused=paused,
            speed=speed,
            winner_text=winner_text,
            next1=next1,
            next2=next2,
            hold1=game.state1.hold_piece if game.state1.has_hold else None,
            hold2=game.state2.hold_piece if game.state2.has_hold else None,
        )





        clock.tick(30 if paused else speed)

    pygame.quit()

if __name__ == "__main__":
    main()
