#include <iostream>
#include "../include/raylib.h"

#define font_size 12

class Button
{
    public :
        int button_01_x;
        int button_01_y;
        int button_01_w;
        int button_01_h;
        int text_w;

        Button ( int button_01_x, int button_01_y, int button_01_w, int button_01_h )
        {
            this -> button_01_x = button_01_x;
            this -> button_01_y = button_01_y;
            this -> button_01_w = button_01_w;
            this -> button_01_h = button_01_h;
        }

        ~ Button ( ) { }
};

// void new_window ( )
// {
//     InitWindow ( 200, 200, "Window" );
//     SetTargetFPS ( 60 );

//     while ( ! WindowShouldClose ( ) )
//     {
//         BeginDrawing ( );
//         ClearBackground ( RAYWHITE );
//         DrawText ( "sei la", 10, 10, 12, BLACK );
//         EndDrawing ( );
//     }
// }

class Window
{
    private :
        int width;
        int height;
        int fps;

    public :
        Window ( )
        {
            width = 400;
            height = 500;
            fps = 60;
        }

        bool hover ( Button * button )
        {
            Vector2 mouse = GetMousePosition ( );
            
            return mouse.x >= button -> button_01_x &&
                   mouse.x <= button -> button_01_x + button -> button_01_w &&
                   mouse.y >= button -> button_01_y &&
                   mouse.y <= button -> button_01_y + button -> button_01_h;
        }

        void initial_window ( )
        {
            InitWindow ( width, height, "Window" );
            SetTargetFPS ( fps );

            while ( ! WindowShouldClose ( ) )
            {
                BeginDrawing ( );
                ClearBackground ( RAYWHITE );

                Button * button = new Button ( ( width - 50 ) / 2, 100, 50, 20 );
                int text_w = MeasureText ( "Botao", 12 );
                int text_x = button -> button_01_x + ( button -> button_01_w - text_w ) / 2;
                int text_y = button -> button_01_y + ( button -> button_01_h - font_size ) / 2;

                if ( hover ( button ) )
                {
                    SetMouseCursor ( MOUSE_CURSOR_POINTING_HAND );
                    // new_window ( );
                } else
                    {
                        SetMouseCursor ( MOUSE_CURSOR_DEFAULT );
                    }

                DrawRectangle ( button -> button_01_x, button -> button_01_y, button -> button_01_w, button -> button_01_h, BLACK );
                DrawText ( "Botao", text_x, text_y, font_size, WHITE );
                EndDrawing ( );
            }
        }
};

int main ( )
{
    Window window;

    window.initial_window ( );
    
    return 0;
}