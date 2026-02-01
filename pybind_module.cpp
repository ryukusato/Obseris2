#include <pybind11/pybind11.h>
#include <pybind11/stl.h>

#include "tetris_state.h"
#include "tetris_eval.h"
#include "tetris_gameover.h"
#include "tetris_duel.h"

namespace py = pybind11;

// Board を Python の list[list[int]] に変換
static Board board_from_py(const std::vector<std::vector<int>>& v){
    return v;
}

static std::vector<std::vector<int>> board_to_py(const Board& b){
    return b;
}

PYBIND11_MODULE(tetris_cpp, m) {
    py::class_<Landing>(m, "Landing")
        .def_readonly("board_after", &Landing::board_after)
        .def_readonly("lines_cleared", &Landing::lines_cleared)
        .def_readonly("combo", &Landing::combo)
        .def_readonly("used_hold", &Landing::used_hold)
        .def_readonly("piece", &Landing::piece)
        .def_readonly("kind", &Landing::kind)
        .def_readonly("attack", &Landing::attack)
        .def_readonly("back_to_back", &Landing::back_to_back)
        .def_readonly("used_t_piece", &Landing::used_t_piece)
        .def_readonly("final_x", &Landing::final_x)
        .def_readonly("final_y", &Landing::final_y)
        .def_readonly("final_rot", &Landing::final_rot);


    py::class_<GameState>(m, "GameState")
        .def(py::init<unsigned int>())
        .def_readwrite("board", &GameState::board)
        .def_readwrite("current", &GameState::current)
        .def_readwrite("next", &GameState::next)
        .def_readwrite("combo", &GameState::combo)
        .def_readwrite("back_to_back", &GameState::back_to_back)
        .def_readwrite("has_hold", &GameState::has_hold)
        .def_readwrite("hold_piece", &GameState::hold_piece)
        .def_readwrite("spawn_x", &GameState::spawn_x)
        .def_readwrite("spawn_y", &GameState::spawn_y);

    m.def("legal_moves", [](const GameState& s){
        return legal_moves(s);
    });

    m.def("apply_move", [](const GameState& s, const Landing& l){
        return apply_move(s, l);
    });

    m.def("evaluate_landing", [](const Landing& l){
        EvalWeights w;              // CC デフォルト重み
        return evaluate_landing(l, w);
    });

    m.def("is_dead", &is_dead,
        py::arg("board"), py::arg("piece"));

    
    m.def("is_dead_state", &is_dead_state);

    m.def("enumerate_drop_landings_from_board",
        &enumerate_drop_landings_from_board);
  
    m.def("judge_winner", &judge_winner,
        py::arg("board1"), py::arg("piece1"),
        py::arg("board2"), py::arg("piece2"));

        py::enum_<Winner>(m, "Winner")
        .value("None", Winner::None)
        .value("Player1", Winner::Player1)
        .value("Player2", Winner::Player2)
        .value("Draw", Winner::Draw);
    
    m.def("get_shape_cells",
        [](PieceType p, int rot) {
            const Coords& c = get_shape(p, rot);
            std::vector<std::pair<int,int>> out(c.begin(), c.end());
            return out;
        });
        
    py::enum_<PieceType>(m, "PieceType")
        .value("I", PieceType::I)
        .value("O", PieceType::O)
        .value("T", PieceType::T)
        .value("S", PieceType::S)
        .value("Z", PieceType::Z)
        .value("J", PieceType::J)
        .value("L", PieceType::L)
        .export_values();
    
  
}
