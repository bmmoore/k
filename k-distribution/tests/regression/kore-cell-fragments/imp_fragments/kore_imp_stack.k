// Copyright (c) 2014-2015 K Team. All Rights Reserved.
requires "domains.k"

module IMP-CORE-SYNTAX
  imports EMPTY-ID
  imports INT-SYNTAX
  imports BOOL-SYNTAX

  syntax AExp  ::= Int | Id
                 | "pop" "(" ")"
                 | AExp "/" AExp              [left, strict]
                 > AExp "+" AExp              [left, strict]
                 | "(" AExp ")"               [bracket]
  syntax BExp  ::= Bool
                 | AExp "<=" AExp             [seqstrict, latex({#1}\leq{#2})]
                 | "!" BExp                   [strict]
                 > BExp "&&" BExp             [left, strict(1)]
                 | "(" BExp ")"               [bracket]
  syntax Block ::= "{" "}"
                 | "{" Stmt "}"
  syntax Stmt  ::= Block
                 | Id "=" AExp ";"            [strict(2)]
                 | "if" "(" BExp ")"
                   Block "else" Block         [strict(1)]
                 | "while" "(" BExp ")" Block
                 | "push" "(" AExp ")"        [strict]
                 | "local" "(" Id ")" Block
                 > Stmt Stmt                  [left]
  syntax Pgm ::= "int" Ids ";" Stmt
  syntax Ids ::= List{Id,","}
endmodule

module IMP-SYNTAX
  imports ID
  imports IMP-CORE-SYNTAX
endmodule

module IMP
  imports IMP-CORE-SYNTAX
  imports MAP
  imports INT

  syntax KResult ::= Int | Bool
  syntax Ints ::= List{Int,","} [klabel(appInts)]

  syntax KItem ::= restore(Id,TCellFragment)

  configuration <T color="yellow">
                  <k color="green"> $PGM:Pgm:K </k>
                  <state color="red"> .Map </state>
                  <stack> .Ints </stack>
                </T>

// AExp
  rule <k> X:Id => I ...</k> <state>... X |-> I ...</state>
  rule I1:Int / I2:Int => I1 /Int I2  when I2 =/=Int 0
  rule I1:Int + I2:Int => I1 +Int I2
  rule <k>pop() => I::Int ...</k> <stack>I,Stk => Stk</stack>
// BExp
  rule I1:Int <= I2:Int => I1 <=Int I2
  rule ! T:Bool => notBool T
  rule true && B => B
  rule false && _ => false
// Block
  rule {} => .K   [structural]
  rule {S} => S  [structural]
// Stmt
  rule <k> X = I:Int; => .K ...</k> <state>... X |-> `_ => I` ...</state>
  rule S1::Stmt S2::Stmt => S1 ~> S2  [structural]
  rule if (true)  S else _ => S
  rule if (false) _ else S => S
  rule while (B) S => if (B) {S while (B) S} else {}  [structural]
  rule <k> push(I:Int) => .K ...</k><stack>Stk => I,Stk</stack>
  rule <T><k>local(X) B => B ~> restore(X,Ctx) ...</k>Ctx</T>
  rule <T><k>restore(X,Ctx) => X = V; ...</k>`<stack>_</stack><state> X |-> V ...</state> => Ctx`</T>
// Pgm
  rule <k> int `X,Xs => Xs`;_ </k> <state> Rho:Map `.Map => X|->0` </state>
    when notBool `X in keys(Rho)`
  rule int .Ids; S => S  [structural]
endmodule
