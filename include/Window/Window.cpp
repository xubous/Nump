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
    int bx0 = ( width - BUTTON_WIDTH ) / 2;
    int by0 = BUTTON_START_Y;
    Button button_0 ( 0, bx0, by0, BUTTON_WIDTH, BUTTON_HEIGHT );
    int text_w0 = MeasureText ( "Notes", BUTTON_FONT_SIZE );
    int text_x0 = bx0 + ( BUTTON_WIDTH - text_w0 ) / 2;
    int text_y0 = by0 + ( BUTTON_HEIGHT - BUTTON_FONT_SIZE ) / 2;
    bool hover_0 = hover ( & button_0 );
    
    DrawRectangle ( bx0, by0, BUTTON_WIDTH, BUTTON_HEIGHT, hover_0 ? DARKGRAY : BLACK );
    DrawText ( "Notes", text_x0, text_y0, BUTTON_FONT_SIZE, WHITE );
    
    if ( hover_0 && IsMouseButtonPressed ( MOUSE_BUTTON_LEFT ) )
        notes ( );
    
    int bx1 = ( width - BUTTON_WIDTH ) / 2;
    int by1 = BUTTON_START_Y + BUTTON_SPACING;
    Button button_1 ( 1, bx1, by1, BUTTON_WIDTH, BUTTON_HEIGHT );
    int text_w1 = MeasureText ( "", BUTTON_FONT_SIZE );
    int text_x1 = bx1 + ( BUTTON_WIDTH - text_w1 ) / 2;
    int text_y1 = by1 + ( BUTTON_HEIGHT - BUTTON_FONT_SIZE ) / 2;
    bool hover_1 = hover ( & button_1 );
    
    DrawRectangle ( bx1, by1, BUTTON_WIDTH, BUTTON_HEIGHT, hover_1 ? DARKGRAY : BLACK );
    DrawText ( "", text_x1, text_y1, BUTTON_FONT_SIZE, WHITE );
    
    if ( hover_1 && IsMouseButtonPressed ( MOUSE_BUTTON_LEFT ) )
        std::cout << "" << std::endl;
    
    
    int bx2 = ( width - BUTTON_WIDTH ) / 2;
    int by2 = BUTTON_START_Y + ( BUTTON_SPACING * 2 );
    Button button_2 ( 2, bx2, by2, BUTTON_WIDTH, BUTTON_HEIGHT );
    int text_w2 = MeasureText ( "", BUTTON_FONT_SIZE );
    int text_x2 = bx2 + ( BUTTON_WIDTH - text_w2 ) / 2;
    int text_y2 = by2 + ( BUTTON_HEIGHT - BUTTON_FONT_SIZE ) / 2;
    bool hover_2 = hover ( & button_2 );
    
    DrawRectangle ( bx2, by2, BUTTON_WIDTH, BUTTON_HEIGHT, hover_2 ? DARKGRAY : BLACK );
    DrawText ( "", text_x2, text_y2, BUTTON_FONT_SIZE, WHITE );
    
    if ( hover_2 && IsMouseButtonPressed ( MOUSE_BUTTON_LEFT ) )
        std::cout << "" << std::endl;

    int bx3 = ( width - BUTTON_WIDTH ) / 2;
    int by3 = BUTTON_START_Y + ( BUTTON_SPACING * 3 );
    Button button_3 ( 3, bx3, by3, BUTTON_WIDTH, BUTTON_HEIGHT );
    int text_w3 = MeasureText ( "", BUTTON_FONT_SIZE );
    int text_x3 = bx3 + ( BUTTON_WIDTH - text_w3 ) / 2;
    int text_y3 = by3 + ( BUTTON_HEIGHT - BUTTON_FONT_SIZE ) / 2;
    bool hover_3 = hover ( & button_3 );
    
    DrawRectangle ( bx3, by3, BUTTON_WIDTH, BUTTON_HEIGHT, hover_3 ? DARKGRAY : BLACK );
    DrawText ( "", text_x3, text_y3, BUTTON_FONT_SIZE, WHITE );
    
    if ( hover_3 && IsMouseButtonPressed ( MOUSE_BUTTON_LEFT ) )
        std::cout << "" << std::endl;
    
    if ( hover_0 || hover_1 || hover_2 || hover_3 )
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

    const char* title = "Nump";
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