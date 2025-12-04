#include "Window.hpp"

Window::Window ( )
{
    width = WINDOW_WIDTH;
    height = WINDOW_HEIGHT;
    fps = FPS;
}

bool Window::hover ( Button * button )
{
    Vector2 mouse = GetMousePosition ( );
    
    return  mouse.x >= button -> button_01_x &&
            mouse.x <= button -> button_01_x + button -> button_01_w &&
            mouse.y >= button -> button_01_y &&
            mouse.y <= button -> button_01_y + button -> button_01_h;
}

void Window::draw_buttons ( )
{
    bool any_button_hovered = false;
    
    for ( int i = 0; i < BUTTON_COUNT; i ++ )
    {
        int bx = ( width - BUTTON_WIDTH ) / 2;
        int by = BUTTON_START_Y + i * BUTTON_SPACING;

        Button button ( bx, by, BUTTON_WIDTH, BUTTON_HEIGHT );

        int text_w = MeasureText ( "Botao", BUTTON_FONT_SIZE );
        int text_x = bx + ( BUTTON_WIDTH - text_w ) / 2;
        int text_y = by + ( BUTTON_HEIGHT - font_size ) / 2;

        if ( hover ( & button ) )
        {
            any_button_hovered = true;
        }

        DrawRectangle ( bx, by, BUTTON_WIDTH, BUTTON_HEIGHT, BLACK );
        DrawText ( "Botao", text_x, text_y, font_size, WHITE );
    }
    
    if ( any_button_hovered )
        SetMouseCursor ( MOUSE_CURSOR_POINTING_HAND );
    else
        SetMouseCursor ( MOUSE_CURSOR_DEFAULT );
}

void Window::draw_title_card ( )
{
    int card_w = width - ( CARD_MARGIN * 2 );
    int card_h = CARD_HEIGHT;
    int card_x = CARD_MARGIN;
    int card_y = CARD_MARGIN;

    DrawRectangleRounded (
        { ( float ) card_x, ( float ) card_y, ( float ) card_w, ( float ) card_h },
        CARD_ROUNDNESS, CARD_SEGMENTS, LIGHTGRAY
    );

    const char* title = "Meu App";
    int text_w = MeasureText ( title, TITLE_FONT_SIZE );
    int text_x = card_x + ( card_w - text_w ) / 2;
    int text_y = card_y + ( card_h - TITLE_FONT_SIZE ) / 2;

    DrawText ( title, text_x, text_y, TITLE_FONT_SIZE, BLACK );
}

void Window::create_window ( )
{
    InitWindow ( width, height, "Window" );
    SetTargetFPS ( fps );

    while ( ! WindowShouldClose ( ) )
    {
        BeginDrawing ( );
        ClearBackground ( RAYWHITE );
        draw_title_card ( );
        draw_buttons ( );
        EndDrawing ( );
    }

    CloseWindow ( );
}