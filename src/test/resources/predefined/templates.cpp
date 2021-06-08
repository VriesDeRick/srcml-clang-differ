template<int f, int g>
int fTemplate(){
    return f;
}

template<class T1, typename T2>
class withType {
    public:
        withType();
};

template <bool>
struct A {
    A(bool) { }
    A(void*) { }
};

int main() {
    fTemplate<0,1>();
    A<false> called(true);
    A<true> *d = 0;
    const int b = 1;
    new A< b >(1);
}