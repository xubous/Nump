g++ tests_01.cpp -o game.exe -lraylib -lopengl32 -lgdi32 -lwinmm
g++ tests_02.cpp ../include/Window/Window.cpp ../include/Button/Button.cpp -o app -lraylib -lopengl32 -lgdi32 -lwinmm