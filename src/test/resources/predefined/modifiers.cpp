namespace N1 {
    namespace N2 {
        class Nested {};

        int nf() {
            return 42;
        }
    }
}

class B{
    public:
        int z;
};

class A{
    public:
        int a;

        B* b;

        void elseWhere(volatile N1::N2::Nested);

        int f() {
            return 5;
        }
};
void A::elseWhere(volatile N1::N2::Nested arg) {
    N1::N2::Nested{};
}

extern N1::N2::Nested externalThing;

int main() {
    int array1[1];
    A* obj = nullptr;
    B b{};
    int namespace_result = N1::N2::nf();
    int B::*ptiptr = &B::z;
    A local;
    obj = &local;
    array1[0];
    &array1;
    b.z;
    obj->b;
    b.*ptiptr = 10;
    (&b)->*ptiptr = 11;
    return 0;
}