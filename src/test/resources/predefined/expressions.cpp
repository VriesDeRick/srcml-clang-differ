#include <typeinfo>

int main() {
    int a = 1e2;
    char b = 'b';
    bool c = 1 > 2 ? true : false;
    int d = main();
    int array[] = {1,1+1,1*1,main(),a};
    auto f1 = [a, b] (int argumentToLambda) -> int { return a + 1; };
    auto f2 = [] () { ; return 1; };
    (int) 1.5;
    (int *) 200;
    (int*) 300;
    sizeof(int);
    sizeof('\n');
    typeid(int);
    typeid(1);
    return 0;
}