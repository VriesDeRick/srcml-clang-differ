class Base;
int main();

enum SmallEnum {
    FIRST,
    SECOND
};

enum LargeEnum: int {
    ONE = 1,
    TWO = 'b'
};

namespace A {
    int withinA;
    struct StructA {};
}

class Base{
    int implicitPrivate;
    friend int main();
    protected:
    public:
        int a();
     explicit inline Base(int c) {
        this->a();
     }

    virtual ~Base() {}

};

class Child : protected Base {
    using Base::a;
    // friend class Base; // Somehow breaks srcML due to it adding macro-tags to constructor
    private:
        int y;
        int z;
        virtual ~Child() {}
    public:
        Child() : Base(1), y{}, z(A::withinA) {}
};

int main() {
    using namespace A;
    using A::withinA;
}
