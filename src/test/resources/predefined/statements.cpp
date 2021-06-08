#include <stdexcept>

void b(int z, ...) {
    return;
}

int main() {
        start:
            for (;;) int a = 5;
        for (int i = 0; i < 1 + 2; ++i) int a = 5;
        int array1[] = { 1 };
        for (int a : array1) { a++; }

        while (1 == 5) 1;
        while (1 < 5) {
            continue;
            int loop = 5;
        }
        long int a = static_cast<long>(0);
        a = 1;

        switch (a) {
            case 1:
                a = 4;
                a = 3;
                break;
            case 0:
            case 2:
                a = 5;
            default:
                a = 6;
        }
        if (a) a = 2;
        if (a) {} else { a = 3; }
        if (a) return 0;
        else if (!a) return 1;
        else if (a) { return 2; }
        else {
            return 3;
        }

        do {
            int a;
        } while (1);

        do 3;
        while (true);


        try {
            1;
        } catch (const std::overflow_error& e) {
            2;
        } catch (int) {
            throw;
        } catch (...) {
            ;
        }
}