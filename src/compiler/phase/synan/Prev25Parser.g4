parser grammar Prev25Parser;

@header {

	package compiler.phase.synan;
	
	import java.util.*;
	import compiler.common.report.*;
	import compiler.phase.lexan.*;
	import compiler.phase.abstr.*;
}

@members {

	private Location loc(Token tok) { return new Location((LexAn.LocLogToken)tok); }
	private Location loc(Token     tok1, Token     tok2) { return new Location((LexAn.LocLogToken)tok1, (LexAn.LocLogToken)tok2); }
	private Location loc(Token     tok1, Locatable loc2) { return new Location((LexAn.LocLogToken)tok1, loc2); }
	private Location loc(Locatable loc1, Token     tok2) { return new Location(loc1, (LexAn.LocLogToken)tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }

}

options{
    tokenVocab=Prev25Lexer;
}

// MISC
id_colon_type_partial returns [AST.CompDefn ast]
    : IDENT COLON type { $ast = new AST.CompDefn(this.loc($IDENT, $type.stop), $IDENT.text, $type.ast); }
    ;
id_colon_type returns [List<AST.CompDefn> astList]
    : rest+=id_colon_type_partial (COMMA rest+=id_colon_type_partial)* {
        $astList = new ArrayList<AST.CompDefn>();
        for (Id_colon_type_partialContext ctx : $rest) {
            $astList.add(ctx.ast);
        }
    } ;
par_id_colon_type_partial returns [AST.ParDefn ast]
    : IDENT COLON type { $ast = new AST.ParDefn(this.loc($IDENT, $type.stop), $IDENT.text, $type.ast); }
    ;
par_id_colon_type returns [List<AST.ParDefn> astList]
    : rest+=par_id_colon_type_partial (COMMA rest+=par_id_colon_type_partial)* {
        $astList = new ArrayList<AST.ParDefn>();
        for (Par_id_colon_type_partialContext ctx : $rest) {
            $astList.add(ctx.ast);
        }
    } ;

// PROGRAM
source returns [AST.Nodes<AST.FullDefn> ast]
	: ( defs += def )+ EOF { 
        ArrayList<AST.FullDefn> list = new ArrayList<AST.FullDefn>();
        for (DefContext d : $defs) {
            list.add(d.ast);
        }
        $ast = new AST.Nodes<AST.FullDefn>(list);
    }
	;

// DEFINITIONS
def returns [AST.FullDefn ast]
    : TYP IDENT EQ type { $ast = new AST.TypDefn(this.loc($TYP, $type.stop), $IDENT.text, $type.ast); }
    | VAR IDENT COLON type { $ast = new AST.VarDefn(this.loc($VAR, $type.stop), $IDENT.text, $type.ast); }
    | FUN IDENT LPAREN (par_id_colon_type)? RPAREN COLON type { 
        List<AST.ParDefn> pars = new ArrayList<AST.ParDefn>();
        // Check if there are any arguments and add them if applicable
        if ($par_id_colon_type.start != null) {
            pars = $par_id_colon_type.astList;
        }
        $ast = new AST.ExtFunDefn(this.loc($FUN, $type.stop), $IDENT.text, pars, $type.ast);
    }
    | FUN IDENT LPAREN (par_id_colon_type)? RPAREN COLON type EQ stmts {  
        List<AST.ParDefn> pars = new ArrayList<AST.ParDefn>();
        // Check if there are any arguments and add them if applicable
        if ($par_id_colon_type.start != null) {
            pars = $par_id_colon_type.astList;
        }
        $ast = new AST.DefFunDefn(this.loc($FUN, $type.stop), $IDENT.text, pars, $type.ast, $stmts.stmtList);
     }
    ;

// STATEMENTS
stmts returns [List<AST.Stmt> stmtList]
    : stmt COMMA stmts { $stmts.stmtList.add($stmt.ast); $stmtList = $stmts.stmtList; }
    | stmt { $stmtList = new ArrayList<AST.Stmt>(); $stmtList.add($stmt.ast); }
    ;

stmt returns [AST.Stmt ast]
    : expr { $ast = null; }
    | expr EQ expr { $ast = null; }
    | RETURN expr { $ast = null; }
    | WHILE expr DO stmts? END { $ast = null; }
    | IF expr THEN stmts? END { $ast = null; }
    | IF expr THEN stmts? ELSE stmts? END { $ast = null; }
    | LET def+ IN stmts END  { $ast = null; }
    ; 

// TYPES
type returns [AST.Type ast]
    : IDENT { $ast = null; }
    | INT { $ast = new AST.AtomType(this.loc($INT), AST.AtomType.Type.INT); }
    | CHAR { $ast = new AST.AtomType(this.loc($CHAR), AST.AtomType.Type.CHAR); }
    | BOOL { $ast = new AST.AtomType(this.loc($BOOL), AST.AtomType.Type.BOOL); }
    | VOID { $ast = new AST.AtomType(this.loc($VOID), AST.AtomType.Type.VOID); }
    | LBRACKET INTCONST RBRACKET type { $ast = new AST.ArrType(this.loc($LBRACKET, $type.stop), $type.ast, $INTCONST.text); }
    | POWER type { $ast = new AST.PtrType(this.loc($POWER, $type.stop), $type.ast); }
    | LT id_colon_type GT { $ast = new AST.StrType(this.loc($LT, $GT), $id_colon_type.astList); }
    | LBRACE id_colon_type RBRACE { $ast = new AST.UniType(this.loc($LBRACE, $RBRACE), $id_colon_type.astList); }
    | LPAREN (typeList+=type (COMMA typeList+=type)*)? RPAREN COLON retType=type { 
        List<AST.Type> parTypes = new ArrayList<AST.Type>();
        for (TypeContext ctx : $typeList) {
            parTypes.add(ctx.ast);
        }
        $ast = new AST.FunType(this.loc($LPAREN, $retType.stop), parTypes, $retType.ast); 
    }
    ;

// EXPRESSIONS
expr : or ;
or : or OR and | and ;
and : and AND comp | comp ;
comp 
    : add EQ_EQ add 
    | add NEQ add
    | add LT add
    | add GT add
    | add GTE add
    | add LTE add
    | add
    ;
add 
    : add PLUS mul 
    | add MINUS mul
    | mul 
    ;
mul
    : mul STAR prefix
    | mul SLASH prefix
    | mul PERCENT prefix
    | prefix
    ;
prefix
    : PLUS prefix
    | MINUS prefix
    | BANG prefix
    | POWER prefix
    | postfix
    ;
postfix
    : postfix LPAREN (expr (COMMA expr)*)? RPAREN
    | postfix LBRACKET expr RBRACKET
    | postfix POWER
    | postfix DOT IDENT
    | const
    ;
const
    : INTCONST
    | CHARCONST
    | STRCONST
    | TRUE
    | FALSE
    | NULL
    | SIZEOF type
    | IDENT
    | LPAREN expr RPAREN
    | LBRACE expr COLON type RBRACE
    ;
