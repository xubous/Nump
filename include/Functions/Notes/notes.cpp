#include "./notes.hpp"
#include "../../Raylib/raylib.h"

FILE * create_file ( string name_file )
{
    FILE * file = fopen ( name_file.c_str ( ), "w+" );

    if ( ! file )
    {
        cout << "Error Create File" << "\n";

        return NULL;
    }

    return file;
}

void notes ( )
{
    std::cout << "Entrou na funcao" << "\n";
}

/*
[  ] espaço com anotações, crud de arquivos e pastas
[  ] anotações novas
[  ] editar existentes
*/