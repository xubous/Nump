#ifndef BUTTON
#define BUTTON

class Button
{
    public :
        int button_id;
        int button_01_x;
        int button_01_y;
        int button_01_w;
        int button_01_h;
        int text_w;

        Button ( int id, int button_01_x, int button_01_y, int button_01_w, int button_01_h );
        ~ Button ( );
};


#endif