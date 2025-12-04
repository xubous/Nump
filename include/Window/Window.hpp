#ifndef WINDOW
#define WINDOW

#include <iostream>
#include "../Raylib/raylib.h"
#include "../Button/Button.hpp"

#define font_size 12

const int WINDOW_WIDTH = 300;
const int WINDOW_HEIGHT = 400;
const int FPS = 60;
const int BUTTON_WIDTH = 50;
const int BUTTON_HEIGHT = 20;
const int BUTTON_SPACING = 40;
const int BUTTON_START_Y = 100;
const int BUTTON_COUNT = 4;
const int CARD_MARGIN = 20;
const int CARD_HEIGHT = 60;
const float CARD_ROUNDNESS = 0.2f;
const int CARD_SEGMENTS = 8;
const int TITLE_FONT_SIZE = 24;
const int BUTTON_FONT_SIZE = 12;

class Window
{
    private :
        int width;
        int height;
        int fps;

    public :
        Window ( );
        bool hover ( Button * button );
        void create_window ( );
        void draw_buttons ( );
        void draw_title_card ( );
};

#endif