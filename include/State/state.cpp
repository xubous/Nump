// enum GameState 
// {
//     MAIN_MENU,
//     NOTES_SCREEN,
//     OPTIONS_SCREEN
// };

// class Window
// {
//     private:
//         GameState current_state;
        
//     public:
//         void create_window ( )
//         {
//             InitWindow ( width, height, "Window" );
//             SetTargetFPS ( fps );
            
//             current_state = MAIN_MENU;
            
//             while ( ! WindowShouldClose ( ) )
//             {
//                 BeginDrawing ( );
//                 ClearBackground ( RAYWHITE );
                
//                 switch ( current_state )
//                 {
//                     case MAIN_MENU:
//                         draw_title_card ( );
//                         draw_buttons ( );
//                         break;
                        
//                     case NOTES_SCREEN:
//                         draw_notes_screen ( );
//                         break;
                        
//                     case OPTIONS_SCREEN:
//                         draw_options_screen ( );
//                         break;
//                 }
                
//                 EndDrawing ( );
//             }
            
//             CloseWindow ( );
//         }
        
//         void change_state ( GameState new_state )
//         {
//             current_state = new_state;
//         }
// };