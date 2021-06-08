class B{
    public:
        int z;
};

class A{
    public:
        int a;

        B* b;

        int f() {
            return 5;
        }
};



int b(int z) {
    return 1;
}

int main() {
    int array1[] = {1,2,3};
    char array2[b(1)];
    A* obj = nullptr;
    float f = 1.5;
    char string[] = "123";
    //char b = 'b';
    int c(int);
    int call = b(1);
    int sum = obj->f();
    sum = array1[0]+sum+(3+4)*5*b(1)*obj->a;
    sum = obj->b->z;
    B b{};
    int B::*ptiptr = &B::z;
    *obj = A{};
    array1[0];
    *obj;
    &array1;
    b.z;
    obj->b;
    b.*ptiptr = 10;
    (&b)->*ptiptr = 11;
    return 0;
}