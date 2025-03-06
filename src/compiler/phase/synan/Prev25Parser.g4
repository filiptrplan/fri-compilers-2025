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
    private AST.Expr binExpr(AST.Expr fst, AST.Expr snd, AST.Expr.Oper op) {
        return new AST.BinExpr(this.loc(fst.location(), snd.location()), op, fst, snd); 
    }
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
    //: expr { $ast = new AST.ExprStmt(null, $expr.ast); }
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
expr returns [AST.Expr ast] : or { $ast = $or.ast; } ;
or returns [AST.Expr ast] 
    : r1=or OR r2=and { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.OR); }
    | and { $ast = $and.ast; }
    ;
and returns [AST.Expr ast]
    : r1=and AND r2=comp { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.AND); }
    | comp { $ast = $comp.ast; }
    ;
comp returns [AST.Expr ast] 
    : r1=add EQ_EQ r2=add { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.EQU); }
    | r1=add NEQ r2=add { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.NEQ); }
    | r1=add LT r2=add { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.LTH); }
    | r1=add GT r2=add { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.GTH); }
    | r1=add GTE r2=add { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.GEQ); }
    | r1=add LTE r2=add { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.LEQ); }
    | add { $ast = $add.ast; }
    ;
add returns [AST.Expr ast] 
    : r1=add PLUS r2=mul { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.ADD); }
    | r1=add MINUS r2=mul { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.SUB); } 
    | mul { $ast = $mul.ast; }
    ;
mul returns [AST.Expr ast]
    : r1=mul STAR r2=prefix { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.MUL); }
    | r1=mul SLASH r2=prefix { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.DIV); }
    | r1=mul PERCENT r2=prefix { $ast = this.binExpr($r1.ast, $r2.ast, AST.BinExpr.Oper.MOD); }
    | prefix { $ast = $prefix.ast; }
    ;
prefix returns [AST.Expr ast]
    : PLUS prefix { $ast = new AST.PfxExpr(this.loc($PLUS, $prefix.stop), AST.PfxExpr.Oper.ADD, $prefix.ast); }
    | MINUS prefix { $ast = new AST.PfxExpr(this.loc($MINUS, $prefix.stop), AST.PfxExpr.Oper.SUB, $prefix.ast); }
    | BANG prefix { $ast = new AST.PfxExpr(this.loc($BANG, $prefix.stop), AST.PfxExpr.Oper.NOT, $prefix.ast); }
    | POWER prefix { $ast = new AST.PfxExpr(this.loc($POWER, $prefix.stop), AST.PfxExpr.Oper.PTR, $prefix.ast); }
    | postfix { $ast = $postfix.ast; }
    ;
postfix returns [AST.Expr ast]
    : postfix LPAREN (exprs += expr (COMMA exprs += expr)*)? RPAREN {
        List<AST.Expr> args = new ArrayList<AST.Expr>();
        for (ExprContext ctx : $exprs) {
            args.add(ctx.ast);
        }
        $ast = new AST.CallExpr(this.loc($postfix.start, $RPAREN), $postfix.ast, args);
    }
    | postfix LBRACKET expr RBRACKET {
        $ast = new AST.ArrExpr(this.loc($postfix.start, $RBRACKET), $postfix.ast, $expr.ast);
    }
    | postfix POWER {
        $ast = new AST.SfxExpr(this.loc($postfix.start, $POWER), AST.SfxExpr.Oper.PTR, $postfix.ast);
    }
    | postfix DOT IDENT {
        $ ast = new AST.CompExpr(this.loc($postfix.start, $IDENT), $postfix.ast, $IDENT.text);
    }
    | const { $ast = $const.ast; }
    ;
const returns [AST.Expr ast]
    : INTCONST { $ast = new AST.AtomExpr(this.loc($INTCONST), AST.AtomExpr.Type.INT, $INTCONST.text); }
    | CHARCONST { $ast = new AST.AtomExpr(this.loc($CHARCONST), AST.AtomExpr.Type.CHAR, $CHARCONST.text); }
    | STRCONST { $ast = new AST.AtomExpr(this.loc($STRCONST), AST.AtomExpr.Type.STR, $STRCONST.text); }
    | TRUE { $ast = new AST.AtomExpr(this.loc($TRUE), AST.AtomExpr.Type.BOOL, $TRUE.text); }
    | FALSE { $ast = new AST.AtomExpr(this.loc($FALSE), AST.AtomExpr.Type.BOOL, $FALSE.text); }
    | NULL { $ast = new AST.AtomExpr(this.loc($NULL), AST.AtomExpr.Type.PTR, $NULL.text); }
    | SIZEOF type { $ast = new AST.SizeExpr(this.loc($SIZEOF, $type.stop), $type.ast); }
    | IDENT { $ast = new AST.NameExpr(this.loc($IDENT), $IDENT.text); }
    | LPAREN expr RPAREN { $ast = $expr.ast; }
    | LBRACE expr COLON type RBRACE { $ast = new AST.CastExpr(this.loc($LBRACE, $RBRACE), $type.ast, $expr.ast); }
    ;
